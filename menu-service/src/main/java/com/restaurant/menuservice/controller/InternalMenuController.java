package com.restaurant.menuservice.controller;

import com.restaurant.menuservice.entity.Food;
import com.restaurant.menuservice.repository.FoodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.lang.NonNull;

@RestController
@RequestMapping("/api/foods")
@RequiredArgsConstructor
public class InternalMenuController {

    private final FoodRepository foodRepository;

    @GetMapping("/prices")
    public Map<Integer, BigDecimal> getFoodPrices(@RequestParam @NonNull List<Integer> foodIds) {
        List<Food> foods = foodRepository.findAllById(foodIds);
        Map<Integer, BigDecimal> prices = new HashMap<>();
        for (Food food : foods) {
            prices.put(food.getId(), food.getPrice());
        }
        return prices;
    }
}
