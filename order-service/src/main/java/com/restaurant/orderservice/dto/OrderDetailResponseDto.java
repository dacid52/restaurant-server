package com.restaurant.orderservice.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class OrderDetailResponseDto {
    private Integer id;
    private Integer food_id;
    private String food_name;
    private String menu_item_name;
    private Integer quantity;
    private BigDecimal price;
    private BigDecimal line_total;
}
