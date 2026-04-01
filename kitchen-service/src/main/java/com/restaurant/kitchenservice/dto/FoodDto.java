package com.restaurant.kitchenservice.dto;

import lombok.Data;
import java.util.List;

@Data
public class FoodDto {
    private Integer id;
    private String name;
    private List<IngredientDto> ingredients;
}
