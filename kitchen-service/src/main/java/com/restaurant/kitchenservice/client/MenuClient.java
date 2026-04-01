package com.restaurant.kitchenservice.client;

import com.restaurant.kitchenservice.dto.FoodDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "menu-service", url = "${services.menu:http://localhost:3002}")
public interface MenuClient {
    @GetMapping("/api/menu/foods/{id}")
    FoodDto getFoodById(@PathVariable("id") Integer id);
}
