package com.restaurant.menuservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "food_ingredients")
@Getter
@Setter
public class FoodIngredient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "food_id", nullable = false)
    private Integer foodId;

    @Column(name = "ingredient_id", nullable = false)
    private Integer ingredientId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;
}
