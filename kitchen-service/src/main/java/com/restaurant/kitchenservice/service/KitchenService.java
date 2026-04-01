package com.restaurant.kitchenservice.service;

import com.restaurant.kitchenservice.client.OrderClient;
import com.restaurant.kitchenservice.entity.KitchenQueue;
import com.restaurant.kitchenservice.repository.KitchenQueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.lang.NonNull;

@Service
@RequiredArgsConstructor
@Slf4j
public class KitchenService {

    private final KitchenQueueRepository kitchenQueueRepository;
    private final OrderClient orderClient;
    private final SocketService socketService;

    public List<Map<String, Object>> getQueue(String status) {
        return kitchenQueueRepository.getQueueWithDetails(status);
    }

    public Map<String, Object> getStats() {
        return kitchenQueueRepository.getKitchenStats();
    }

    @Transactional
    public void receiveOrderItems(List<Integer> orderDetailIds) {
        for (Integer odId : orderDetailIds) {
            // Skip if already exists (prevent duplicate on re-confirm)
            Integer existing = kitchenQueueRepository.countByOrderDetailId(odId);
            if (existing != null && existing > 0) {
                log.info("KitchenQueue entry already exists for order_detail_id={}, skipping", odId);
                continue;
            }
            KitchenQueue queue = new KitchenQueue();
            queue.setOrderDetailId(odId);
            kitchenQueueRepository.save(queue);
        }

        // 🔔 Emit real-time event — mirrors: io.emit('new_order_items', ...)
        Map<String, Object> payload = new HashMap<>();
        payload.put("added_items", orderDetailIds);
        socketService.emitNewOrderItems(payload);
    }

    @Transactional
    public KitchenQueue updateQueueItemStatus(@NonNull Integer id, String status) {
        KitchenQueue item = kitchenQueueRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy món trong hàng đợi"));

        item.setStatus(status);
        kitchenQueueRepository.save(item);

        // 🔔 Emit queue status update — mirrors: io.emit('queue_status_updated', ...)
        Map<String, Object> queuePayload = new HashMap<>();
        queuePayload.put("id", item.getId());
        queuePayload.put("order_detail_id", item.getOrderDetailId());
        queuePayload.put("status", item.getStatus());
        queuePayload.put("updated_at", item.getUpdatedAt());
        socketService.emitQueueStatusUpdated(queuePayload);

        // 🔔 If completed, emit delivered event — mirrors: io.emit('queue_item_delivered', ...)
        if ("Hoàn thành".equals(status)) {
            socketService.emitQueueItemDelivered(queuePayload);
        }

        try {
            Integer orderId = kitchenQueueRepository.findOrderIdByOrderDetailId(item.getOrderDetailId());
            if (orderId != null) {
                List<String> queueStatuses = kitchenQueueRepository.findAllStatusesForOrder(orderId);
                String newOrderStatus = null;

                if (!queueStatuses.isEmpty()) {
                    boolean allDone = queueStatuses.stream().allMatch(s -> "Hoàn thành".equals(s));
                    boolean anyCooking = queueStatuses.stream().anyMatch(s -> "Đang chế biến".equals(s));
                    boolean anyPending = queueStatuses.stream().anyMatch(s -> "Chờ chế biến".equals(s));

                    if (allDone) {
                        newOrderStatus = "Hoàn thành";
                    } else if (anyCooking) {
                        newOrderStatus = "Đang nấu";
                    } else if (anyPending) {
                        newOrderStatus = "Đang nấu";
                    }
                }

                if (newOrderStatus != null) {
                    log.info("Syncing order {} status -> {}", orderId, newOrderStatus);
                    orderClient.updateOrderStatus(orderId, Map.of("status", newOrderStatus));

                    // 🔔 Emit order status update — mirrors: io.emit('order_status_updated', ...)
                    Map<String, Object> orderPayload = new HashMap<>();
                    orderPayload.put("order_id", orderId);
                    orderPayload.put("status", newOrderStatus);
                    socketService.emitOrderStatusUpdated(null, orderPayload);
                }
            }
        } catch (Exception e) {
            log.error("Lỗi khi đồng bộ trạng thái đơn hàng", e);
        }

        return item;
    }

    @Transactional
    public void deleteQueueItem(@NonNull Integer id) {
        kitchenQueueRepository.deleteById(id);

        // 🔔 Emit queue update after deletion
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", id);
        payload.put("deleted", true);
        socketService.emitQueueStatusUpdated(payload);
    }

    @Transactional
    public void clearCompletedItems() {
        kitchenQueueRepository.deleteByStatus("Hoàn thành");

        // 🔔 Emit cleared — mirrors: io.emit('completed_items_cleared', ...)
        Map<String, Object> payload = new HashMap<>();
        payload.put("cleared", true);
        socketService.emitCompletedItemsCleared(payload);
    }
}
