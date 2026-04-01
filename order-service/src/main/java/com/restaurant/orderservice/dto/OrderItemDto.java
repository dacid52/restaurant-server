package com.restaurant.orderservice.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;

@Data
public class OrderItemDto {
    @NotNull
    private Integer food_id;
    
    @NotNull
    @Min(1)
    private Integer quantity;
}
