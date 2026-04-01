package com.restaurant.menuservice.repository;

import com.restaurant.menuservice.entity.Food;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FoodRepository extends JpaRepository<Food, Integer> {
    List<Food> findByCategoryId(Integer categoryId);
    long countByCategoryId(Integer categoryId);
}
