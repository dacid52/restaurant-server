package com.restaurant.paymentservice.service;

import com.restaurant.paymentservice.client.OrderClient;
import com.restaurant.paymentservice.entity.Payment;
import com.restaurant.paymentservice.entity.PaymentRequest;
import com.restaurant.paymentservice.repository.PaymentRepository;
import com.restaurant.paymentservice.repository.PaymentRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRequestRepository paymentRequestRepository;
    private final PaymentRepository paymentRepository;
    private final OrderClient orderClient;
    private final SocketService socketService;
    private final MomoService momoService;

    public List<Map<String, Object>> getWaitingPayments() {
        List<Map<String, Object>> requests = paymentRequestRepository.getWaitingPaymentsWithTableKey();
        Map<String, Map<String, Object>> sessionMap = new HashMap<>();

        for (Map<String, Object> req : requests) {
            Integer tableId = toInteger(req.get("table_id"));
            String tableKey = req.get("table_key") != null ? String.valueOf(req.get("table_key")) : null;
            String sessionKey = tableId + "_" + (tableKey != null ? tableKey : "no_key");

            if (!sessionMap.containsKey(sessionKey)) {
                Integer orderCount = (tableId != null && tableKey != null)
                        ? paymentRequestRepository.countUnpaidOrdersForSession(tableId, tableKey)
                        : 1;

                if (orderCount == null || orderCount == 0) {
                    orderCount = 1;
                }

                Map<String, Object> sessionData = new HashMap<>(req);
                sessionData.put("order_count", orderCount);
                sessionMap.put(sessionKey, sessionData);
            } else {
                Map<String, Object> existing = sessionMap.get(sessionKey);
                BigDecimal currentTotal = (BigDecimal) req.get("total");
                BigDecimal existingTotal = (BigDecimal) existing.get("total");
                existing.put("total", existingTotal.add(currentTotal));

                LocalDateTime currentRequestTime = toLocalDateTime(req.get("request_time"));
                LocalDateTime existingRequestTime = toLocalDateTime(existing.get("request_time"));
                if (currentRequestTime.isBefore(existingRequestTime)) {
                    existing.put("request_time", currentRequestTime);
                }

                // Nếu bất kỳ đơn nào dùng MoMo → ưu tiên hiển thị MoMo cho session
                String currentMethod = (String) req.get("payment_method");
                if ("momo".equals(currentMethod)) {
                    existing.put("payment_method", "momo");
                    if (req.get("momo_trans_id") != null) {
                        existing.put("momo_trans_id", req.get("momo_trans_id"));
                    }
                }
            }
        }
        log.info("📋 Waiting payments grouped: {} rows -> {} sessions", requests.size(), sessionMap.size());
        return new ArrayList<>(sessionMap.values());
    }

    private Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(value));
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) return LocalDateTime.now();
        if (value instanceof LocalDateTime ldt) return ldt;
        if (value instanceof Timestamp ts) return ts.toLocalDateTime();
        return LocalDateTime.parse(String.valueOf(value).replace(" ", "T"));
    }

    @Transactional
    public void createPaymentRequest(Integer orderId, Integer tableId, String tableKey, BigDecimal amount, String paymentMethod) {
        log.info("💾 Creating payment request - Order: {}, Table: {}, Amount: {}", orderId, tableId, amount);
        String normalizedMethod = "momo".equalsIgnoreCase(paymentMethod) ? "momo" : "cash";

        Optional<PaymentRequest> existingOpt = paymentRequestRepository.findByOrderIdAndStatus(orderId, "waiting");
        if (existingOpt.isPresent()) {
            PaymentRequest existing = existingOpt.get();
            log.info("📝 Updating existing payment request - Old amount: {}, New amount: {}", existing.getTotal(), amount);
            existing.setTotal(amount);
            existing.setPaymentMethod(normalizedMethod);
            existing.setRequestTime(LocalDateTime.now());
            paymentRequestRepository.save(existing);
        } else {
            PaymentRequest newRequest = new PaymentRequest();
            newRequest.setOrderId(orderId);
            newRequest.setTableId(tableId);
            newRequest.setTotal(amount);
            newRequest.setStatus("waiting");
            newRequest.setPaymentMethod(normalizedMethod);
            paymentRequestRepository.save(newRequest);
            log.info("✨ New payment request saved - ID: {}, Amount: {}", newRequest.getId(), newRequest.getTotal());
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("order_id", orderId);
        payload.put("table_id", tableId);
        payload.put("table_key", tableKey);
        payload.put("amount", amount);
        payload.put("payment_method", normalizedMethod);

        log.info("📤 Broadcasting webhook payload: {}", payload);
        socketService.emitPaymentRequest(payload);
    }

    @Transactional
    public Map<String, Object> processCashPayment(Integer orderId, Integer tableId, String tableKey, String authHeader) {
        log.info("💳 Processing cash payment - Order: {}, Table: {}, TableKey: {}", orderId, tableId, tableKey);

        PaymentRequest request = paymentRequestRepository.findByOrderIdAndStatus(orderId, "waiting")
                .orElseThrow(() -> {
                    log.error("❌ Payment request not found for order: {}", orderId);
                    return new RuntimeException("Không tìm thấy yêu cầu thanh toán");
                });

        // BUG-013: Validate order còn tồn tại trong order-service
        try {
            Map<String, Object> orderData = orderClient.getOrder(orderId);
            if (orderData == null) {
                throw new RuntimeException("Đơn hàng không tồn tại");
            }
            Object paymentStatus = orderData.get("payment_status");
            if ("paid".equals(paymentStatus)) {
                throw new RuntimeException("Đơn hàng đã được thanh toán trước đó");
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.warn("⚠️ Không thể xác thực order từ order-service: {}", e.getMessage());
        }

        Integer finalTableId = tableId != null ? tableId : request.getTableId();
        String finalTableKey = tableKey;
        if (finalTableKey == null) {
            finalTableKey = paymentRequestRepository.getTableKeyForOrder(orderId);
        }

        if (finalTableId == null || finalTableKey == null) {
            throw new RuntimeException("Thiếu thông tin bàn hoặc table_key để hoàn tất thanh toán");
        }

        List<Integer> allOrderIds = paymentRequestRepository.findWaitingOrderIdsForSession(finalTableId, finalTableKey);
        if (allOrderIds == null || allOrderIds.isEmpty()) {
            allOrderIds = List.of(orderId);
        }

        List<PaymentRequest> sessionRequests = paymentRequestRepository.findByOrderIdInAndStatus(allOrderIds, "waiting");
        if (sessionRequests.isEmpty()) {
            sessionRequests = List.of(request);
        }

        BigDecimal sessionTotal = BigDecimal.ZERO;
        for (PaymentRequest paymentRequest : sessionRequests) {
            // BUG-014: Kiểm tra lại status trước khi xử lý — tránh double payment
            if (!"waiting".equals(paymentRequest.getStatus())) {
                log.warn("⚠️ PaymentRequest {} already has status '{}', skipping", paymentRequest.getId(), paymentRequest.getStatus());
                continue;
            }
            paymentRequest.setStatus("paid");
            paymentRequestRepository.save(paymentRequest);

            Payment payment = new Payment();
            payment.setOrderId(paymentRequest.getOrderId());
            payment.setAmount(paymentRequest.getTotal());
            // Lấy method từ payment_request (cash hoặc momo)
            String method = paymentRequest.getPaymentMethod() != null ? paymentRequest.getPaymentMethod() : "cash";
            payment.setMethod(method);
            paymentRepository.save(payment);

            sessionTotal = sessionTotal.add(paymentRequest.getTotal());
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("table_id", finalTableId);
            payload.put("table_key", finalTableKey);
            payload.put("order_ids", allOrderIds);

            log.info("📤 Calling order-service to complete payment...");
            Map<String, Object> completionRes = orderClient.completePayment(authHeader, payload);
            log.info("✅ Order service response: {}", completionRes);

            if (completionRes.containsKey("order_ids")) {
                @SuppressWarnings("unchecked")
                List<Integer> fetchedIds = (List<Integer>) completionRes.get("order_ids");
                allOrderIds = fetchedIds;
            }
        } catch (Exception e) {
            log.error("❌ Error calling order-service: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể hoàn tất phiên thanh toán tại order-service", e);
        }

        Map<String, Object> wsPayload = new HashMap<>();
        wsPayload.put("request_id", request.getId());
        wsPayload.put("order_id", orderId);
        wsPayload.put("order_ids", allOrderIds);
        wsPayload.put("table_id", finalTableId);
        wsPayload.put("amount", sessionTotal);
        socketService.emitPaymentCompleted(wsPayload);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Thanh toán thành công");
        result.put("order_count", allOrderIds.size());
        result.put("total_amount", sessionTotal);
        return result;
    }

    public List<Map<String, Object>> getPaymentHistory() {
        return paymentRepository.getPaymentHistory();
    }

    // ---------------------------------------------------------------
    // MoMo payment methods
    // ---------------------------------------------------------------

    /**
     * Tạo link thanh toán MoMo sandbox.
     * Trả về {payUrl, deeplink, qrCodeUrl, ...} từ MoMo API.
     */
    public Map<String, Object> createMomoPayment(Integer orderId, Integer tableId, String tableKey, BigDecimal amount) {
        try {
            // Chỉ tạo link MoMo. Payment request đã được tạo ở luồng request-payment.
            Map<String, Object> momoResponse = momoService.createPayment(orderId, tableId, tableKey, amount);
            int resultCode = ((Number) momoResponse.getOrDefault("resultCode", -1)).intValue();
            if (resultCode != 0) {
                String message = (String) momoResponse.getOrDefault("message", "Lỗi tạo thanh toán MoMo");
                throw new RuntimeException("MoMo API lỗi: " + message);
            }
            return momoResponse;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("❌ Lỗi gọi MoMo API: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể kết nối MoMo: " + e.getMessage(), e);
        }
    }

    /**
     * Cập nhật momo_trans_id sau khi IPN về (khách đã quét QR thành công).
     */
    @Transactional
    public void updateMomoTransId(Integer orderId, String transId) {
        paymentRequestRepository.findByOrderIdAndStatus(orderId, "waiting").ifPresent(pr -> {
            pr.setMomoTransId(transId);
            paymentRequestRepository.save(pr);
            log.info("✅ Updated momo_trans_id for order {}: {}", orderId, transId);
        });
    }

    /**
     * Xác nhận thanh toán MoMo thành công và hoàn tất thanh toán phiên bàn.
     */
    @Transactional
    public Map<String, Object> confirmMomoPayment(Integer orderId, Integer tableId,
                                                   String tableKey, String transId,
                                                   BigDecimal amount) {
        log.info("💜 Confirm MoMo payment - Order:{}, Table:{}, TransId:{}", orderId, tableId, transId);

        PaymentRequest pr = paymentRequestRepository.findByOrderIdAndStatus(orderId, "waiting")
                .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu thanh toán MoMo đang chờ"));

        pr.setPaymentMethod("momo");
        if (transId != null && !transId.isBlank()) {
            pr.setMomoTransId(transId);
        }
        paymentRequestRepository.save(pr);

        try {
            Map<String, Object> result = processCashPayment(orderId, tableId, tableKey, null);
            result.put("payment_method", "momo");
            result.put("momo_trans_id", pr.getMomoTransId());
            result.put("message", "Thanh toán MoMo thành công, bàn đã được đóng");
            return result;
        } catch (RuntimeException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("đã được thanh toán")) {
                return new HashMap<>(Map.of(
                        "success", true,
                        "message", "Giao dịch MoMo đã được xác nhận trước đó",
                        "payment_method", "momo",
                        "momo_trans_id", pr.getMomoTransId()
                ));
            }
            throw e;
        }
    }
}
