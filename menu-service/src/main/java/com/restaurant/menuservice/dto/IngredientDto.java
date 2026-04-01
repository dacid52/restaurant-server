package com.restaurant.menuservice.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IngredientDto {
    private Integer id;
    private String name;
    private String unit;
    private BigDecimal amount;
}
