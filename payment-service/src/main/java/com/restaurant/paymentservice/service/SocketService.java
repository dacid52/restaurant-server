package com.restaurant.paymentservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service to broadcast WebSocket events to connected clients.
 * Mirrors the Socket.IO emit logic from Node.js payment-service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SocketService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Emit event equivalent to: io.emit('payment_request', data)
     * Frontend subscribes to /topic/payment.request
     */
    public void emitPaymentRequest(Map<String, Object> data) {
        log.info("🔔 WebSocket emit payment_request: tableId={}", data.get("table_id"));
        messagingTemplate.convertAndSend("/topic/payment.request", data);
    }

    /**
     * Emit event equivalent to: io.emit('payment_completed', data)
     * Frontend subscribes to /topic/payment.completed
     */
    public void emitPaymentCompleted(Map<String, Object> data) {
        log.info("✅ WebSocket emit payment_completed: tableId={}", data.get("table_id"));
        messagingTemplate.convertAndSend("/topic/payment.completed", data);
    }
}
