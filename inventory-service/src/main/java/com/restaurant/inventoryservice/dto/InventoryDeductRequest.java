package com.restaurant.inventoryservice.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class InventoryDeductRequest {
    private Integer ingredientId;
    private BigDecimal amount;
}
