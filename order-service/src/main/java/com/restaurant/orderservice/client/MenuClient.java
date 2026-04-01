package com.restaurant.orderservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@FeignClient(name = "menu-service", url = "${services.menu}")
public interface MenuClient {

    @GetMapping("/api/foods/prices")
    Map<Integer, BigDecimal> getFoodPrices(@RequestParam("foodIds") List<Integer> foodIds);
}
