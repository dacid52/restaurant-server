package com.restaurant.menuservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "inventory-service", url = "${services.inventory}")
public interface InventoryClient {

    @GetMapping("/api/inventory/ingredients/details")
    Map<Integer, Map<String, String>> getIngredientDetails(@RequestParam("ingredientIds") List<Integer> ingredientIds);
}
