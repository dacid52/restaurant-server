package com.restaurant.paymentservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_requests")
@Getter
@Setter
public class PaymentRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "order_id", nullable = false)
    private Integer orderId;

    @Column(name = "table_id", nullable = false)
    private Integer tableId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal total;

    @Column(name = "request_time")
    private LocalDateTime requestTime;

    @Column(length = 20)
    private String status = "waiting";

    @Column(name = "payment_method", length = 20, nullable = false)
    private String paymentMethod = "cash";

    @Column(name = "momo_trans_id", length = 100)
    private String momoTransId;

    @PrePersist
    public void prePersist() {
        if (this.requestTime == null) {
            this.requestTime = LocalDateTime.now();
        }
    }
}
