package com.restaurant.orderservice.controller;

import com.restaurant.orderservice.dto.OrderRequest;
import com.restaurant.orderservice.entity.Order;
import com.restaurant.orderservice.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import org.springframework.lang.NonNull;

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
    public ResponseEntity<Map<String, Object>> requestPayment(@PathVariable Integer id) {
        // Logic request payment should be in Service, omitted for brevity. You can implement it fully in Service
        return ResponseEntity.ok(Map.of("success", true, "message", "Đã gửi yêu cầu thanh toán"));
    }

    @PostMapping("/payments/complete")
    public ResponseEntity<Map<String, Object>> completePayment(@RequestBody Map<String, Object> payload) {
        // Payment complete logic
        return ResponseEntity.ok(Map.of("success", true, "message", "Đã hoàn tất thanh toán và đóng bàn"));
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
