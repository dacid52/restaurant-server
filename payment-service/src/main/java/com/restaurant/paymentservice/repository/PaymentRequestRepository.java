package com.restaurant.paymentservice.repository;

import com.restaurant.paymentservice.entity.PaymentRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PaymentRequestRepository extends JpaRepository<PaymentRequest, Integer> {

    Optional<PaymentRequest> findByOrderIdAndStatus(Integer orderId, String status);

    List<PaymentRequest> findByOrderIdInAndStatus(List<Integer> orderIds, String status);

    @Query(value = "SELECT pr.order_id, pr.table_id, pr.total, pr.request_time, pr.status, o.table_key, " +
            "pr.payment_method, pr.momo_trans_id " +
            "FROM paymentdb.payment_requests pr " +
            "LEFT JOIN orderdb.orders o ON pr.order_id = o.id " +
            "WHERE pr.status = 'waiting' " +
            "ORDER BY pr.request_time ASC", nativeQuery = true)
    List<Map<String, Object>> getWaitingPaymentsWithTableKey();

    @Query(value = "SELECT COUNT(*) as order_count " +
            "FROM orderdb.orders " +
            "WHERE table_id = :tableId AND table_key = :tableKey " +
            "AND (payment_status IS NULL OR payment_status <> 'paid')", nativeQuery = true)
    Integer countUnpaidOrdersForSession(Integer tableId, String tableKey);

    @Query(value = "SELECT table_key FROM orderdb.orders WHERE id = :orderId", nativeQuery = true)
    String getTableKeyForOrder(Integer orderId);

    @Query(value = "SELECT pr.order_id " +
            "FROM paymentdb.payment_requests pr " +
            "JOIN orderdb.orders o ON pr.order_id = o.id " +
            "WHERE pr.status = 'waiting' " +
            "AND o.table_id = :tableId " +
            "AND o.table_key = :tableKey " +
            "ORDER BY pr.request_time ASC", nativeQuery = true)
    List<Integer> findWaitingOrderIdsForSession(Integer tableId, String tableKey);
}
