package com.restaurant.orderservice.controller;

import com.restaurant.orderservice.dto.OrderRequest;
import com.restaurant.orderservice.entity.Order;
import com.restaurant.orderservice.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import org.springframework.lang.NonNull;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<Order> createOrder(@Valid @RequestBody OrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(request));
    }

    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    @GetMapping("/table/{tableId}")
    public ResponseEntity<List<Order>> getOrdersByTable(@PathVariable Integer tableId, @RequestParam String tableKey) {
        return ResponseEntity.ok(orderService.getOrdersByTable(tableId, tableKey));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(@PathVariable @NonNull Integer id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Order> updateOrderStatus(@PathVariable @NonNull Integer id, @RequestBody Map<String, String> payload) {
        String status = payload.get("status");
        return ResponseEntity.ok(orderService.updateOrderStatus(id, status));
    }

    @PostMapping("/{id}/request-payment")
    public ResponseEntity<Map<String, Object>> requestPayment(@PathVariable @NonNull Integer id) {
        orderService.requestPayment(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Đã gửi yêu cầu thanh toán"));
    }

    @PostMapping("/complete-payment")
    public ResponseEntity<Map<String, Object>> completePayment(@RequestBody Map<String, Object> payload) {
        log.info("📥 Complete payment request - Payload: {}", payload);
        
        try {
            Integer tableId = payload.containsKey("table_id") ? ((Number) payload.get("table_id")).intValue() : null;
            String tableKey = (String) payload.get("table_key");
            
            @SuppressWarnings("unchecked")
            List<Integer> orderIds = payload.containsKey("order_ids") ? 
                (List<Integer>) payload.get("order_ids") : List.of();
            
            if (tableId == null || orderIds.isEmpty()) {
                throw new RuntimeException("Thiếu table_id hoặc order_ids");
            }
            
            log.info("✅ Parsed data - Table: {}, Orders: {}", tableId, orderIds);
            
            Map<String, Object> result = orderService.completePayment(tableId, tableKey, orderIds);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("❌ Error completing payment: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(
                Map.of("success", false, "error", e.getMessage())
            );
        }
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<Map<String, String>> confirmOrder(@PathVariable @NonNull Integer id, HttpServletRequest request) {
        Integer roleId = (Integer) request.getAttribute("roleId");
        if (roleId != null && roleId != 1 && roleId != 2) {
             return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        orderService.confirmOrder(id);
        return ResponseEntity.ok(Map.of("message", "Đã gửi bếp"));
    }
}
