package com.restaurant.tableservice.repository;

import com.restaurant.tableservice.entity.RestaurantLayout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RestaurantLayoutRepository extends JpaRepository<RestaurantLayout, String> {
}
