package com.restaurant.kitchenservice.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class IngredientDto {
    private Integer id;
    private String name;
    private String unit;
    private BigDecimal amount;
}
