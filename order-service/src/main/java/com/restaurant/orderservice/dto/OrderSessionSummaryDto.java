package com.restaurant.orderservice.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class OrderSessionSummaryDto {
    private Integer representative_order_id;
    private Integer table_id;
    private String table_key;
    private Integer total_orders;
    private Integer total_items;
    private BigDecimal total_amount;
    private String status;
    private String payment_status;
    private Boolean buffet_active;
    private String buffet_package_name;
    /** Luồng buffet đang chờ thu ngân xác nhận */
    private Boolean has_pending_buffet;
    private Integer pending_buffet_order_id;
    private String pending_buffet_package_name;
    private BigDecimal pending_buffet_price;
    private LocalDateTime last_order_time;
}
