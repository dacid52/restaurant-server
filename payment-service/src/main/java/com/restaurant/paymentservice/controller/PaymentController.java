package com.restaurant.paymentservice.controller;

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

    @GetMapping("/waiting")
    public ResponseEntity<List<Map<String, Object>>> getWaitingPayments() {
        return ResponseEntity.ok(paymentService.getWaitingPayments());
    }

    @PostMapping("/request")
    public ResponseEntity<Map<String, Boolean>> createPaymentRequest(@RequestBody Map<String, Object> payload) {
        log.info("📥 Nhận payment request: {}", payload);
        
        Integer orderId = (Integer) payload.get("order_id");
        Integer tableId = (Integer) payload.get("table_id");
        String tableKey = (String) payload.get("table_key");
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

        paymentService.createPaymentRequest(orderId, tableId, tableKey, amount);
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
}
