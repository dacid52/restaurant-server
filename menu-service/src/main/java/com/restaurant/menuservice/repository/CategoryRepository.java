package com.restaurant.menuservice.repository;

import com.restaurant.menuservice.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Integer> {
}
