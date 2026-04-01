package com.restaurant.inventoryservice.repository;

import com.restaurant.inventoryservice.entity.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

public interface IngredientRepository extends JpaRepository<Ingredient, Integer> {
    List<Ingredient> findAllByOrderByNameAsc();
    List<Ingredient> findByQuantityLessThanOrderByQuantityAsc(BigDecimal threshold);
    
    @Query(value = "SELECT COUNT(*) FROM MenuDB.food_ingredients WHERE ingredient_id = :id", nativeQuery = true)
    int countUsageInMenu(Integer id);
}
