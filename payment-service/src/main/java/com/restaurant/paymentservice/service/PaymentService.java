package com.restaurant.paymentservice.service;

import com.restaurant.paymentservice.client.OrderClient;
import com.restaurant.paymentservice.entity.Payment;
import com.restaurant.paymentservice.entity.PaymentRequest;
import com.restaurant.paymentservice.repository.PaymentRepository;
import com.restaurant.paymentservice.repository.PaymentRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRequestRepository paymentRequestRepository;
    private final PaymentRepository paymentRepository;
    private final OrderClient orderClient;
    private final SocketService socketService;

    public List<Map<String, Object>> getWaitingPayments() {
        List<Map<String, Object>> requests = paymentRequestRepository.getWaitingPaymentsWithTableKey();
        Map<String, Map<String, Object>> sessionMap = new HashMap<>();

        for (Map<String, Object> req : requests) {
            Integer tableId = (Integer) req.get("table_id");
            String tableKey = (String) req.get("table_key");
            String sessionKey = tableId + "_" + (tableKey != null ? tableKey : "no_key");

            if (!sessionMap.containsKey(sessionKey)) {
                Integer orderCount = tableKey != null
                        ? paymentRequestRepository.countUnpaidOrdersForSession(tableId, tableKey)
                        : 1;

                if (orderCount == null || orderCount == 0) orderCount = 1;

                Map<String, Object> sessionData = new HashMap<>(req);
                sessionData.put("order_count", orderCount);
                sessionMap.put(sessionKey, sessionData);
            } else {
                Map<String, Object> existing = sessionMap.get(sessionKey);
                BigDecimal currentTotal = (BigDecimal) req.get("total");
                BigDecimal existingTotal = (BigDecimal) existing.get("total");
                
                if (currentTotal.compareTo(existingTotal) > 0) {
                    existing.put("total", currentTotal);
                }
                
                LocalDateTime currentRequestTime = (LocalDateTime) req.get("request_time");
                LocalDateTime existingRequestTime = (LocalDateTime) existing.get("request_time");
                if (currentRequestTime.isBefore(existingRequestTime)) {
                    existing.put("request_time", currentRequestTime);
                }
            }
        }
        return new ArrayList<>(sessionMap.values());
    }

    @Transactional
    public void createPaymentRequest(Integer orderId, Integer tableId, String tableKey, BigDecimal amount) {
        Optional<PaymentRequest> existingOpt = paymentRequestRepository.findByOrderIdAndStatus(orderId, "waiting");
        if (existingOpt.isPresent()) {
            PaymentRequest existing = existingOpt.get();
            existing.setTotal(amount);
            existing.setRequestTime(LocalDateTime.now());
            paymentRequestRepository.save(existing);
        } else {
            PaymentRequest newRequest = new PaymentRequest();
            newRequest.setOrderId(orderId);
            newRequest.setTableId(tableId);
            newRequest.setTotal(amount);
            newRequest.setStatus("waiting");
            paymentRequestRepository.save(newRequest);
        }

        // 🔔 Broadcost event to cashiers
        Map<String, Object> payload = new HashMap<>();
        payload.put("order_id", orderId);
        payload.put("table_id", tableId);
        payload.put("table_key", tableKey);
        payload.put("amount", amount);
        socketService.emitPaymentRequest(payload);
    }

    @Transactional
    public Map<String, Object> processCashPayment(Integer orderId, Integer tableId, String tableKey, String authHeader) {
        PaymentRequest request = paymentRequestRepository.findByOrderIdAndStatus(orderId, "waiting")
                .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu thanh toán"));

        Integer finalTableId = tableId != null ? tableId : request.getTableId();
        String finalTableKey = tableKey;
        if (finalTableKey == null) {
            finalTableKey = paymentRequestRepository.getTableKeyForOrder(orderId);
        }

        if (finalTableId == null || finalTableKey == null) {
            throw new RuntimeException("Thiếu thông tin bàn hoặc table_key để hoàn tất thanh toán");
        }

        request.setStatus("paid");
        paymentRequestRepository.save(request);

        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setAmount(request.getTotal());
        payment.setMethod("cash");
        paymentRepository.save(payment);

        List<Integer> allOrderIds = List.of(orderId);
        
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("table_id", finalTableId);
            payload.put("table_key", finalTableKey);
            payload.put("order_ids", allOrderIds);
            
            Map<String, Object> completionRes = orderClient.completePayment(authHeader, payload);
            if (completionRes.containsKey("order_ids")) {
                @SuppressWarnings("unchecked")
                List<Integer> fetchedIds = (List<Integer>) completionRes.get("order_ids");
                allOrderIds = fetchedIds;
            }
        } catch (Exception e) {
            throw new RuntimeException("Không thể hoàn tất phiên thanh toán tại order-service", e);
        }

        // 🔔 Broadcost event to cashiers and customers
        Map<String, Object> wsPayload = new HashMap<>();
        wsPayload.put("request_id", request.getId());
        wsPayload.put("order_id", orderId);
        wsPayload.put("table_id", finalTableId);
        wsPayload.put("amount", request.getTotal());
        socketService.emitPaymentCompleted(wsPayload);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Thanh toán thành công");
        result.put("order_count", allOrderIds.size());
        return result;
    }

    public List<Map<String, Object>> getPaymentHistory() {
        return paymentRepository.getPaymentHistory();
    }
}
