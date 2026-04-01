package com.restaurant.paymentservice.repository;

import com.restaurant.paymentservice.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Map;

public interface PaymentRepository extends JpaRepository<Payment, Integer> {

    @Query(value = "SELECT p.order_id, p.amount, p.method, p.paid_at, r.table_id " +
            "FROM PaymentDB.payments p " +
            "LEFT JOIN PaymentDB.payment_requests r ON p.order_id = r.order_id " +
            "ORDER BY p.paid_at DESC", nativeQuery = true)
    List<Map<String, Object>> getPaymentHistory();
}
