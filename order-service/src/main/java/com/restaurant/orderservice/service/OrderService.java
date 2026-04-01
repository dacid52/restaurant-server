package com.restaurant.orderservice.service;

import com.restaurant.orderservice.client.KitchenClient;
import com.restaurant.orderservice.client.MenuClient;
import com.restaurant.orderservice.client.TableClient;
import com.restaurant.orderservice.dto.OrderItemDto;
import com.restaurant.orderservice.dto.OrderRequest;
import com.restaurant.orderservice.entity.Order;
import com.restaurant.orderservice.entity.OrderDetail;
import com.restaurant.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.lang.NonNull;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final TableClient tableClient;
    private final MenuClient menuClient;
    private final KitchenClient kitchenClient;
    private final SocketService socketService;

    @Transactional
    public Order createOrder(OrderRequest request) {
        boolean isStaffRequest = request.getUser_id() != null;

        if (!isStaffRequest && request.getTable_key() == null) {
            throw new RuntimeException("Thiếu table_key");
        }

        if (request.getTable_key() != null) {
            Boolean isValid = true;
            try {
               isValid = tableClient.validateTableKey(request.getTable_id(), request.getTable_key());
            } catch (Exception e){
                log.warn("Lỗi validate table key, giả lập trả về true phục vụ demo");
                // Giả lập trả về true nếu service chưa start
            }
            if (isValid != null && !isValid) {
                throw new RuntimeException("Key không hợp lệ hoặc đã hết hạn");
            }
        }

        List<Integer> foodIds = request.getItems().stream()
                .map(OrderItemDto::getFood_id)
                .distinct()
                .collect(Collectors.toList());

        Map<Integer, BigDecimal> foodPriceMap = new HashMap<>();
        if (!foodIds.isEmpty()) {
            try {
                foodPriceMap = menuClient.getFoodPrices(foodIds);
            } catch (Exception e){
                log.warn("Lỗi get food prices, giả lập giá default");
                for (Integer id : foodIds) {
                    foodPriceMap.put(id, new BigDecimal("100000"));
                }
            }
        }

        BigDecimal total = BigDecimal.ZERO;
        if (Boolean.TRUE.equals(request.getIs_buffet())) {
             total = request.getBuffet_price() != null ? request.getBuffet_price() : new BigDecimal("299000");
        } else {
             for (OrderItemDto item : request.getItems()) {
                 BigDecimal price = foodPriceMap.get(item.getFood_id());
                 if (price == null) throw new RuntimeException("Không tìm thấy món " + item.getFood_id());
                 total = total.add(price.multiply(new BigDecimal(item.getQuantity())));
             }
        }

        Order order = new Order();
        order.setTableId(request.getTable_id());
        order.setUserId(request.getUser_id());
        order.setTotal(total);
        order.setTableKey(request.getTable_key());
        order.setIsBuffet(request.getIs_buffet());
        order.setStatus("Chờ xác nhận");
        order.setPaymentStatus("unpaid");

        for (OrderItemDto item : request.getItems()) {
            OrderDetail detail = new OrderDetail();
            detail.setOrder(order);
            detail.setFoodId(item.getFood_id());
            detail.setQuantity(item.getQuantity());
            
            if (Boolean.TRUE.equals(request.getIs_buffet())) {
                detail.setPrice(BigDecimal.ZERO);
            } else {
                detail.setPrice(foodPriceMap.get(item.getFood_id()));
            }
            order.getDetails().add(detail);
        }

        Order savedOrder = orderRepository.save(order);

        try {
            tableClient.updateTableStatus(request.getTable_id(), "Chờ xác nhận", false);
        } catch (Exception e) {
            log.warn("Lỗi update table status, ignore for demo");
        }

        // 🔔 Broadcost event to trigger UI updates
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", savedOrder.getId());
        payload.put("table_id", savedOrder.getTableId());
        payload.put("status", savedOrder.getStatus());
        socketService.emitOrderCreated(savedOrder.getTableId(), payload);

        return savedOrder;
    }

    public List<Order> getOrdersByTable(Integer tableId, String tableKey) {
        return orderRepository.findByTableIdAndTableKeyAndPaymentStatusNot(tableId, tableKey, "paid");
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public Order getOrderById(@NonNull Integer id) {
        return orderRepository.findById(id).orElseThrow(() -> new RuntimeException("Không tìm thấy đơn"));
    }

    @Transactional
    public Order updateOrderStatus(@NonNull Integer id, String status) {
        Order order = getOrderById(id);
        order.setStatus(status);
        Order savedOrder = orderRepository.save(order);

        // 🔔 Broadcast
        Map<String, Object> payload = new HashMap<>();
        payload.put("order_id", savedOrder.getId());
        payload.put("table_id", savedOrder.getTableId());
        payload.put("status", savedOrder.getStatus());
        socketService.emitOrderStatusUpdated(savedOrder.getTableId(), payload);

        return savedOrder;
    }
    
    @Transactional
    public void confirmOrder(@NonNull Integer id) {
        Order order = getOrderById(id);
        if ("paid".equals(order.getPaymentStatus())) {
             throw new RuntimeException("Đơn đã thanh toán");
        }

        if (!"Chờ xác nhận".equals(order.getStatus())) {
            log.warn("Order {} is already in status '{}', cannot re-confirm", id, order.getStatus());
            throw new RuntimeException("Đơn hàng đã được xác nhận trước đó (trạng thái: " + order.getStatus() + ")");
        }

        List<Integer> detailIds = order.getDetails().stream()
                .map(OrderDetail::getId)
                .collect(java.util.stream.Collectors.toList());

        try {
            kitchenClient.notifyNewOrder(Map.of("added_items", detailIds));
        } catch (Exception e) {
            log.warn("Kitchen service không khả dụng: {}", e.getMessage());
        }

        order.setStatus("Đang nấu");
        Order savedOrder = orderRepository.save(order);

        // 🔔 Broadcast
        Map<String, Object> payload = new HashMap<>();
        payload.put("order_id", savedOrder.getId());
        payload.put("table_id", savedOrder.getTableId());
        payload.put("status", savedOrder.getStatus());
        socketService.emitOrderStatusUpdated(savedOrder.getTableId(), payload);
    }
}
