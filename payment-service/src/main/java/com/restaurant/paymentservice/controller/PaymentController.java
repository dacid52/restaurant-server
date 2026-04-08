package com.restaurant.paymentservice.controller;

import com.restaurant.paymentservice.service.MomoService;
import com.restaurant.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final MomoService momoService;

    @GetMapping("/waiting")
    public ResponseEntity<List<Map<String, Object>>> getWaitingPayments() {
        try {
            List<Map<String, Object>> rows = paymentService.getWaitingPayments();
            log.info("📥 GET /payments/waiting -> {} sessions", rows.size());
            return ResponseEntity.ok(rows);
        } catch (Exception e) {
            log.error("❌ GET /payments/waiting error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @PostMapping("/request")
    public ResponseEntity<Map<String, Boolean>> createPaymentRequest(@RequestBody Map<String, Object> payload) {
        log.info("📥 Nhận payment request: {}", payload);
        
        Integer orderId = ((Number) payload.get("order_id")).intValue();
        Integer tableId = ((Number) payload.get("table_id")).intValue();
        String tableKey = (String) payload.get("table_key");
        String paymentMethod = String.valueOf(payload.getOrDefault("payment_method", "cash"));
        Object amountObj = payload.get("amount");
        
        log.info("📊 Parsed data - Order: {}, Table: {}, TableKey: {}, Amount: {} (type: {})", 
                orderId, tableId, tableKey, amountObj, amountObj != null ? amountObj.getClass().getSimpleName() : "null");
        
        if (amountObj == null) {
            log.error("❌ Amount is null! Full Payload: {}", payload);
            return ResponseEntity.badRequest().body(Map.of("success", false));
        }
        
        BigDecimal amount = null;
        try {
            if (amountObj instanceof BigDecimal) {
                amount = (BigDecimal) amountObj;
            } else if (amountObj instanceof Number) {
                amount = new BigDecimal(amountObj.toString());
            } else {
                amount = new BigDecimal(amountObj.toString());
            }
        } catch (Exception e) {
            log.error("❌ Failed to parse amount: {}, Error: {}", amountObj, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false));
        }
        
        log.info("✅ Amount successfully converted to BigDecimal: {} (value: {})", amount, amount.toPlainString());

        paymentService.createPaymentRequest(orderId, tableId, tableKey, amount, paymentMethod);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/process/cash")
    public ResponseEntity<Map<String, Object>> processCashPayment(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        log.info("📥 Process cash payment request - Payload: {}", payload);
        
        // Validate required fields
        if (payload == null || payload.isEmpty()) {
            log.error("❌ Empty payload!");
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Payload không được trống"));
        }
        
        Integer orderId = null;
        Integer tableId = null;
        String tableKey = null;
        
        try {
            Object orderIdObj = payload.get("order_id");
            if (orderIdObj == null) {
                log.error("❌ order_id is null!");
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "order_id là bắt buộc"));
            }
            orderId = ((Number) orderIdObj).intValue();
            
            Object tableIdObj = payload.get("table_id");
            if (tableIdObj == null) {
                log.error("❌ table_id is null!");
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "table_id là bắt buộc"));
            }
            tableId = ((Number) tableIdObj).intValue();
            
            tableKey = (String) payload.get("table_key");
            
            log.info("✅ Parsed payment data - Order: {}, Table: {}, TableKey: {}", orderId, tableId, tableKey);
        } catch (Exception e) {
            log.error("❌ Failed to parse payload: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Dữ liệu không hợp lệ: " + e.getMessage()));
        }
        
        try {
            Map<String, Object> result = paymentService.processCashPayment(orderId, tableId, tableKey, authHeader);
            log.info("✅ Payment processed successfully");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("❌ Error processing payment: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/history")
    public ResponseEntity<List<Map<String, Object>>> getPaymentHistory() {
        return ResponseEntity.ok(paymentService.getPaymentHistory());
    }

    // ---------------------------------------------------------------
    // MoMo endpoints
    // ---------------------------------------------------------------

    /**
     * Khách gọi để lấy payUrl → redirect sang trang thanh toán MoMo sandbox.
     */
    @PostMapping("/momo/create")
    public ResponseEntity<Map<String, Object>> createMomoPayment(@RequestBody Map<String, Object> payload) {
        log.info("📥 MoMo create request: {}", payload);
        try {
            Integer orderId  = ((Number) payload.get("order_id")).intValue();
            Integer tableId  = ((Number) payload.get("table_id")).intValue();
            String  tableKey = (String)  payload.get("table_key");
            BigDecimal amount = new BigDecimal(payload.get("amount").toString());

            Map<String, Object> result = paymentService.createMomoPayment(orderId, tableId, tableKey, amount);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("❌ MoMo create error: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * MoMo gọi IPN sau khi khách quét QR và thanh toán thành công.
     * Cập nhật momo_trans_id để thu ngân thấy mã giao dịch.
     */
    @PostMapping("/momo/ipn")
    public ResponseEntity<String> momoIpnCallback(@RequestBody Map<String, Object> payload) {
        log.info("📨 MoMo IPN callback: {}", payload);
        // Xác thực chữ ký HMAC-SHA256 — từ chối nếu bị giả mạo
        if (!momoService.verifyIpnSignature(payload)) {
            log.warn("⚠️ MoMo IPN signature mismatch — request bị từ chối");
            return ResponseEntity.badRequest().body("Invalid signature");
        }
        try {
            int resultCode = ((Number) payload.getOrDefault("resultCode", -1)).intValue();
            if (resultCode == 0) {
                // momoOrderId = "MOMO_<orderId>_<timestamp>"
                String momoOrderId = (String) payload.getOrDefault("orderId", "");
                String transId = String.valueOf(payload.getOrDefault("transId", ""));
                BigDecimal amount = new BigDecimal(String.valueOf(payload.getOrDefault("amount", "0")));
                String[] parts = momoOrderId.split("_");
                if (parts.length >= 2) {
                    try {
                        Integer orderId = Integer.parseInt(parts[1]);
                        paymentService.confirmMomoPayment(orderId, null, null, transId, amount);
                        log.info("✅ IPN: payment completed for order={} via transId={}", orderId, transId);
                    } catch (NumberFormatException e) {
                        log.warn("⚠️ Cannot parse orderId from momoOrderId: {}", momoOrderId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("❌ IPN error: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok("0");
    }

    /**
     * Frontend gọi sau khi MoMo redirect về payment-return.html thành công.
     * Hoàn tất thanh toán MoMo và đóng phiên bàn.
     */
    @PostMapping("/momo/notify-cashier")
    public ResponseEntity<Map<String, Object>> momoNotifyCashier(@RequestBody Map<String, Object> payload) {
        log.info("📥 MoMo notify-cashier: {}", payload);
        try {
            int resultCode = ((Number) payload.getOrDefault("result_code", -1)).intValue();
            if (resultCode != 0) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "error", "Thanh toán MoMo không thành công (resultCode=" + resultCode + ")"));
            }

            Integer orderId  = ((Number) payload.get("order_id")).intValue();
            Integer tableId  = ((Number) payload.get("table_id")).intValue();
            String  tableKey = (String)  payload.get("table_key");
            String  transId  = String.valueOf(payload.getOrDefault("trans_id", ""));
            BigDecimal amount = new BigDecimal(payload.get("amount").toString());

            Map<String, Object> result = paymentService.confirmMomoPayment(orderId, tableId, tableKey, transId, amount);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("❌ MoMo notify error: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }
}
