package com.restaurant.paymentservice.repository;

import com.restaurant.paymentservice.entity.PaymentRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PaymentRequestRepository extends JpaRepository<PaymentRequest, Integer> {

    Optional<PaymentRequest> findByOrderIdAndStatus(Integer orderId, String status);

    @Query(value = "SELECT pr.order_id, pr.table_id, pr.total, pr.request_time, pr.status, o.table_key " +
            "FROM PaymentDB.payment_requests pr " +
            "LEFT JOIN OrderDB.orders o ON pr.order_id = o.id " +
            "WHERE pr.status = 'waiting' " +
            "ORDER BY pr.request_time ASC", nativeQuery = true)
    List<Map<String, Object>> getWaitingPaymentsWithTableKey();
    
    @Query(value = "SELECT COUNT(*) as order_count " +
            "FROM OrderDB.orders " +
            "WHERE table_id = :tableId AND table_key = :tableKey " +
            "AND (payment_status IS NULL OR payment_status <> 'paid') " +
            "AND (status IS NULL OR status <> N'Đã thanh toán')", nativeQuery = true)
    Integer countUnpaidOrdersForSession(Integer tableId, String tableKey);
    
    @Query(value = "SELECT table_key FROM OrderDB.orders WHERE id = :orderId", nativeQuery = true)
    String getTableKeyForOrder(Integer orderId);
}
