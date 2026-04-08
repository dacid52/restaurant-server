package com.restaurant.orderservice.repository;

import com.restaurant.orderservice.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Integer> {
    List<Order> findByTableIdAndTableKeyAndPaymentStatusNotOrderByOrderTimeAsc(Integer tableId, String tableKey, String paymentStatus);
    List<Order> findByPaymentStatusNotOrderByOrderTimeAsc(String paymentStatus);
    Order findFirstByTableIdAndTableKeyAndIsBuffetTrueAndPaymentStatusNotAndBuffetSessionIdIsNotNullOrderByOrderTimeDesc(
            Integer tableId, String tableKey, String paymentStatus);
}
