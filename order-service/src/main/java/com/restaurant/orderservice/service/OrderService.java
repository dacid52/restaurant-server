package com.restaurant.orderservice.service;

import com.restaurant.orderservice.client.KitchenClient;
import com.restaurant.orderservice.client.MenuClient;
import com.restaurant.orderservice.client.PaymentClient;
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
    private final PaymentClient paymentClient;
    private final SocketService socketService;

    @Transactional
    public Order createOrder(OrderRequest request) {
        boolean isStaffRequest = request.getUser_id() != null;
        
        log.info("🛒 Creating order - Table: {}, Is Buffet: {}, Items: {}", 
                request.getTable_id(), request.getIs_buffet(), 
                request.getItems() != null ? request.getItems().size() : "NULL");

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
        
        log.info("🍽️ Food IDs from request: {}", foodIds);

        Map<Integer, BigDecimal> foodPriceMap = new HashMap<>();
        if (!foodIds.isEmpty()) {
            try {
                foodPriceMap = menuClient.getFoodPrices(foodIds);
                log.info("✅ Got prices from menu-service: {}", foodPriceMap);
            } catch (Exception e){
                log.warn("⚠️ Lỗi get food prices, giả lập giá default: {}", e.getMessage());
                for (Integer id : foodIds) {
                    foodPriceMap.put(id, new BigDecimal("100000"));
                }
            }
        } else {
            log.warn("⚠️ foodIds is empty!");
        }

        BigDecimal total = BigDecimal.ZERO;
        if (Boolean.TRUE.equals(request.getIs_buffet())) {
             total = request.getBuffet_price() != null ? request.getBuffet_price() : new BigDecimal("299000");
             log.info("📊 Buffet order - Price: {}", total);
        } else {
             for (OrderItemDto item : request.getItems()) {
                 BigDecimal price = foodPriceMap.get(item.getFood_id());
                 if (price == null) {
                     log.error("❌ Food not found in price map - Food ID: {}, Available IDs: {}", item.getFood_id(), foodPriceMap.keySet());
                     throw new RuntimeException("Không tìm thấy món " + item.getFood_id());
                 }
                 BigDecimal itemTotal = price.multiply(new BigDecimal(item.getQuantity()));
                 total = total.add(itemTotal);
                 log.info("📦 Adding item - Food ID: {}, Price: {}, Quantity: {}, Item Total: {}", 
                         item.getFood_id(), price, item.getQuantity(), itemTotal);
             }
             log.info("✅ Order total calculated: {} from {} items", total, request.getItems().size());
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

    @Transactional
    public void requestPayment(@NonNull Integer orderId) {
        Order order = getOrderById(orderId);
        
        log.info("🔔 Yêu cầu thanh toán - Order ID: {}, Total: {}, Items count: {}", 
                orderId, order.getTotal(), order.getDetails() != null ? order.getDetails().size() : 0);
        
        // 🔍 Debug: Check order details
        if (order.getDetails() != null && !order.getDetails().isEmpty()) {
            log.info("📋 Order details:");
            order.getDetails().forEach(detail -> 
                log.info("  - Food ID: {}, Quantity: {}, Price: {}", 
                    detail.getFoodId(), detail.getQuantity(), detail.getPrice())
            );
        } else {
            log.warn("⚠️ Order has NO details!");
        }
        
        // Validate total before sending
        if (order.getTotal() == null || order.getTotal().compareTo(BigDecimal.ZERO) == 0) {
            log.warn("⚠️ Order total is 0 or null! Order ID: {}, Total: {}", orderId, order.getTotal());
        }
        
        // Proxy to Payment Service
        Map<String, Object> req = new HashMap<>();
        req.put("order_id", order.getId());
        req.put("table_id", order.getTableId());
        req.put("table_key", order.getTableKey());
        req.put("amount", order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO);
        
        log.info("📤 Payment request payload: {}", req);
        
        try {
            paymentClient.requestPayment(req);
            log.info("✅ Payment request sent successfully - Amount: {}", req.get("amount"));
        } catch (Exception e) {
            log.error("Lỗi khi gửi yêu cầu thanh toán tới payment-service: {}", e.getMessage(), e);
            throw new RuntimeException("Lỗi hệ thống khi yêu cầu thanh toán. Vui lòng thử lại.");
        }
    }

    @Transactional
    public Map<String, Object> completePayment(Integer tableId, String tableKey, List<Integer> orderIds) {
        log.info("💳 Completing payment - Table: {}, Orders: {}", tableId, orderIds);
        
        if (tableId == null) {
            throw new RuntimeException("Thiếu thông tin bàn (table_id)");
        }

        // 1️⃣ Mark all orders as paid
        List<Order> orders = orderRepository.findAllById(orderIds);
        for (Order order : orders) {
            order.setPaymentStatus("paid");
            orderRepository.save(order);
            log.info("✅ Order {} marked as paid", order.getId());
        }

        // 2️⃣ Invalidate table key
        try {
            tableClient.invalidateTableKey(tableId);
            log.info("🔒 Table key invalidated for table: {}", tableId);
        } catch (Exception e) {
            log.warn("⚠️ Failed to invalidate table key: {}", e.getMessage());
        }

        // 3️⃣ Update table status to empty
        try {
            tableClient.updateTableStatus(tableId, "Trống", false);
            log.info("🧹 Table {} marked as empty", tableId);
        } catch (Exception e) {
            log.warn("⚠️ Failed to update table status: {}", e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Đã hoàn tất thanh toán và đóng bàn");
        result.put("order_ids", orderIds);
        result.put("table_id", tableId);

        log.info("✅ Payment completed successfully - Table {} is now empty", tableId);
        return result;
    }
}

