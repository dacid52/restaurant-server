package com.restaurant.paymentservice.controller;

import com.restaurant.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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
        Integer orderId = (Integer) payload.get("order_id");
        Integer tableId = (Integer) payload.get("table_id");
        String tableKey = (String) payload.get("table_key");
        BigDecimal amount = new BigDecimal(payload.get("amount").toString());

        paymentService.createPaymentRequest(orderId, tableId, tableKey, amount);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/process/cash")
    public ResponseEntity<Map<String, Object>> processCashPayment(
            @RequestBody Map<String, Object> payload,
            @RequestHeader("Authorization") String authHeader) {
        
        Integer orderId = (Integer) payload.get("order_id");
        Integer tableId = payload.containsKey("table_id") ? (Integer) payload.get("table_id") : null;
        String tableKey = (String) payload.get("table_key");
        
        return ResponseEntity.ok(paymentService.processCashPayment(orderId, tableId, tableKey, authHeader));
    }

    @GetMapping("/history")
    public ResponseEntity<List<Map<String, Object>>> getPaymentHistory() {
        return ResponseEntity.ok(paymentService.getPaymentHistory());
    }
}
