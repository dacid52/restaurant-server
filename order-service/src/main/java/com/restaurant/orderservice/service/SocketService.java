package com.restaurant.orderservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import org.springframework.lang.NonNull;

/**
 * Service to broadcast WebSocket events to connected clients.
 * Mirrors Socket.IO emit from Node.js order-service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SocketService {

    private final SimpMessagingTemplate messagingTemplate;

    private void emitTableEvent(Integer tableId, @NonNull String eventName, @NonNull Map<String, Object> data) {
        if (tableId != null) {
            messagingTemplate.convertAndSend("/topic/table." + tableId,
                    Map.of("event", eventName, "data", data));
        }
    }

    /**
     * Broadcast order created event.
     * Frontend subscribes to /topic/order.created
     * Also notifies per-table room: /topic/table.{tableId}
     */
    @SuppressWarnings("null")
    public void emitOrderCreated(Integer tableId, @NonNull Map<String, Object> data) {
        log.info("🛒 WebSocket emit order_created: tableId={}", tableId);
        messagingTemplate.convertAndSend("/topic/order.created", data);
        emitTableEvent(tableId, "order_created", data);
    }

    public void emitBuffetOrderCreated(Integer tableId, @NonNull Map<String, Object> data) {
        log.info("🍽️ WebSocket emit buffet_order_created: tableId={}", tableId);
        emitTableEvent(tableId, "buffet_order_created", data);
    }

    public void emitBuffetFoodAdded(Integer tableId, @NonNull Map<String, Object> data) {
        log.info("🥗 WebSocket emit buffet_food_added: tableId={}", tableId);
        emitTableEvent(tableId, "buffet_food_added", data);
    }

    /**
     * Broadcast order status updated event.
     * Frontend subscribes to /topic/order.status.updated  
     * Also notifies per-table: /topic/table.{tableId}
     */
    @SuppressWarnings("null")
    public void emitOrderStatusUpdated(Integer tableId, @NonNull Map<String, Object> data) {
        log.info("📋 WebSocket emit order_status_updated: tableId={}, status={}", tableId, data.get("status"));
        messagingTemplate.convertAndSend("/topic/order.status.updated", data);
        emitTableEvent(tableId, "order_status_updated", data);
    }

    /**
     * Broadcast payment completed (so customer page can refresh).
     * Frontend subscribes to /topic/payment.completed
     */
    @SuppressWarnings("null")
    public void emitPaymentCompleted(Integer tableId, @NonNull Map<String, Object> data) {
        log.info("💰 WebSocket emit payment_completed: tableId={}", tableId);
        messagingTemplate.convertAndSend("/topic/payment.completed", data);
        emitTableEvent(tableId, "payment_completed", data);
    }
}
