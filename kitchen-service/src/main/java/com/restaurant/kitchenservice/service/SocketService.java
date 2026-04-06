package com.restaurant.kitchenservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import org.springframework.lang.NonNull;

/**
 * Service to broadcast WebSocket events to connected clients.
 * Mirrors Socket.IO emit from Node.js kitchen-service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SocketService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Equivalent to: io.emit('queue_status_updated', data)
     * Frontend subscribes to /topic/kitchen.queue-updated
     */
    public void emitQueueStatusUpdated(@NonNull Map<String, Object> data) {
        log.info("🍳 WebSocket emit queue_status_updated: id={}", data.get("id"));
        messagingTemplate.convertAndSend("/topic/kitchen.queue-updated", data);
    }

    /**
     * Equivalent to: io.emit('new_order_items', data)
     * Frontend subscribes to /topic/kitchen.new-order
     */
    public void emitNewOrderItems(@NonNull Map<String, Object> data) {
        log.info("🆕 WebSocket emit new_order_items");
        messagingTemplate.convertAndSend("/topic/kitchen.new-order", data);
    }

    /**
     * Equivalent to: io.emit('completed_items_cleared', data)
     * Frontend subscribes to /topic/kitchen.cleared
     */
    public void emitCompletedItemsCleared(@NonNull Map<String, Object> data) {
        log.info("🧹 WebSocket emit completed_items_cleared");
        messagingTemplate.convertAndSend("/topic/kitchen.cleared", data);
    }

    /**
     * Equivalent to: io.emit('queue_item_delivered', data)
     * Frontend subscribes to /topic/kitchen.item-delivered  
     */
    public void emitQueueItemDelivered(@NonNull Map<String, Object> data) {
        log.info("🚀 WebSocket emit queue_item_delivered: id={}", data.get("id"));
        messagingTemplate.convertAndSend("/topic/kitchen.item-delivered", data);
    }

    public void emitTableItemStatus(Integer tableId, @NonNull Map<String, Object> data) {
        log.info("🍽️ WebSocket emit table item status: tableId={}, orderDetailId={}", tableId, data.get("order_detail_id"));
        if (tableId != null) {
            messagingTemplate.convertAndSend("/topic/order.item-status." + tableId, data);
        }
    }

    /**
     * Equivalent to: io.emit('order_status_updated', data)
     * Also emitted per-table: /topic/order.status.{tableId}
     */
    public void emitOrderStatusUpdated(Integer tableId, @NonNull Map<String, Object> data) {
        log.info("📋 WebSocket emit order_status_updated: tableId={}", tableId);
        messagingTemplate.convertAndSend("/topic/order.status.updated", data);
        if (tableId != null) {
            messagingTemplate.convertAndSend("/topic/order.status." + tableId, data);
        }
    }
}
