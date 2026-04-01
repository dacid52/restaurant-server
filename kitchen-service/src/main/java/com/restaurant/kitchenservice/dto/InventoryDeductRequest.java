package com.restaurant.kitchenservice.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class InventoryDeductRequest {
    private Integer ingredientId;
    private BigDecimal amount;
    
    public InventoryDeductRequest(Integer ingredientId, BigDecimal amount) {
        this.ingredientId = ingredientId;
        this.amount = amount;
    }
}
