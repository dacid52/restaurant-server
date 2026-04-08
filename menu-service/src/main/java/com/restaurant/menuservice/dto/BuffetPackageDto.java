package com.restaurant.menuservice.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class BuffetPackageDto {
    private Integer id;
    private String name;
    private BigDecimal price;
    private String description;
    private Boolean isActive;
    /** Danh sách food_id thuộc gói này */
    private List<Integer> foodIds;
    /** Danh sách FoodDto chi tiết (dùng cho response) */
    private List<FoodDto> foods;
}
