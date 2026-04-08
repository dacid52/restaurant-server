package com.restaurant.orderservice.controller;

import com.restaurant.orderservice.dto.OrderRequest;
import com.restaurant.orderservice.dto.OrderResponseDto;
import com.restaurant.orderservice.dto.OrderSessionDetailDto;
import com.restaurant.orderservice.dto.OrderSessionSummaryDto;
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

    private static final String ADMIN_ROLE = "ADMIN";
    private static final String STAFF_ROLE = "STAFF";
    private static final String MANAGER_ROLE = "MANAGER";
    private static final String CASHIER_ROLE = "CASHIER";

    private final OrderService orderService;

    @PostMapping
    @SuppressWarnings("null")
    public ResponseEntity<OrderResponseDto> createOrder(@Valid @RequestBody OrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.getOrderResponseById(orderService.createOrder(request).getId()));
    }

    @GetMapping
    public ResponseEntity<List<OrderResponseDto>> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrderResponses());
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<OrderSessionSummaryDto>> getAllSessions() {
        return ResponseEntity.ok(orderService.getAllSessionSummaries());
    }

    @GetMapping("/sessions/detail")
    public ResponseEntity<OrderSessionDetailDto> getSessionDetail(
            @RequestParam Integer tableId,
            @RequestParam String tableKey) {
        return ResponseEntity.ok(orderService.getSessionDetail(tableId, tableKey));
    }

    @PostMapping("/sessions/confirm")
    public ResponseEntity<Map<String, Object>> confirmSessionOrders(
            @RequestBody Map<String, Object> payload,
            HttpServletRequest request) {
        String roleName = (String) request.getAttribute("roleName");
        boolean isStaffLevel = ADMIN_ROLE.equalsIgnoreCase(roleName)
                || STAFF_ROLE.equalsIgnoreCase(roleName)
                || MANAGER_ROLE.equalsIgnoreCase(roleName)
                || CASHIER_ROLE.equalsIgnoreCase(roleName);
        if (!isStaffLevel) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Integer tableId = payload.containsKey("table_id") ? ((Number) payload.get("table_id")).intValue() : null;
        String tableKey = (String) payload.get("table_key");
        return ResponseEntity.ok(orderService.confirmSessionOrders(tableId, tableKey));
    }

    @GetMapping("/table/{tableId}")
    public ResponseEntity<List<OrderResponseDto>> getOrdersByTable(@PathVariable Integer tableId, @RequestParam String tableKey) {
        return ResponseEntity.ok(orderService.getOrderResponsesByTable(tableId, tableKey));
    }

    @GetMapping("/table/{tableId}/session-summary")
    public ResponseEntity<OrderSessionSummaryDto> getSessionSummary(@PathVariable Integer tableId, @RequestParam String tableKey) {
        return ResponseEntity.ok(orderService.getSessionSummary(tableId, tableKey));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponseDto> getOrderById(@PathVariable @NonNull Integer id) {
        return ResponseEntity.ok(orderService.getOrderResponseById(id));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<OrderResponseDto> updateOrderStatus(@PathVariable @NonNull Integer id, @RequestBody Map<String, String> payload) {
        String status = payload.get("status");
        orderService.updateOrderStatus(id, status);
        return ResponseEntity.ok(orderService.getOrderResponseById(id));
    }

    @PostMapping("/{id}/request-payment")
    public ResponseEntity<Map<String, Object>> requestPayment(
            @PathVariable @NonNull Integer id,
            @RequestBody(required = false) Map<String, Object> payload) {
        String tableKey = payload != null ? (String) payload.get("table_key") : null;
        String paymentMethod = payload != null
            ? String.valueOf(payload.getOrDefault("payment_method", "cash"))
            : "cash";
        return ResponseEntity.ok(orderService.requestPayment(id, tableKey, paymentMethod));
    }

    @PostMapping("/complete-payment")
    public ResponseEntity<Map<String, Object>> completePayment(@RequestBody Map<String, Object> payload) {
        log.info("📥 Complete payment request - Payload: {}", payload);

        try {
            Integer tableId = payload.containsKey("table_id") ? ((Number) payload.get("table_id")).intValue() : null;
            String tableKey = (String) payload.get("table_key");

            @SuppressWarnings("unchecked")
            List<Integer> orderIds = payload.containsKey("order_ids")
                ? (List<Integer>) payload.get("order_ids") : List.of();

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
        String roleName = (String) request.getAttribute("roleName");
        boolean isStaffLevel = ADMIN_ROLE.equalsIgnoreCase(roleName)
                || STAFF_ROLE.equalsIgnoreCase(roleName)
                || MANAGER_ROLE.equalsIgnoreCase(roleName)
                || CASHIER_ROLE.equalsIgnoreCase(roleName);
        if (!isStaffLevel) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        orderService.confirmOrder(id);
        return ResponseEntity.ok(Map.of("message", "Đã gửi bếp"));
    }

    /**
     * Đánh dấu các đơn hàng của phiên sang trạng thái 'waiting'
     * (được gọi nội bộ bởi payment-service sau khi khách thanh toán MoMo).
     */
    @PostMapping("/{id}/mark-waiting")
    public ResponseEntity<Map<String, Object>> markWaiting(
            @PathVariable @NonNull Integer id,
            @RequestBody Map<String, String> payload) {
        String tableKey = payload.get("table_key");
        orderService.markOrdersWaiting(id, tableKey);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
