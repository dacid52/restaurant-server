package com.restaurant.inventoryservice.controller;

import com.restaurant.inventoryservice.entity.Ingredient;
import com.restaurant.inventoryservice.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.lang.NonNull;
import com.restaurant.inventoryservice.dto.InventoryDeductRequest;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/ingredients")
    public ResponseEntity<List<Ingredient>> getAllIngredients() {
        return ResponseEntity.ok(inventoryService.getAllIngredients());
    }

    @GetMapping("/ingredients/{id}")
    public ResponseEntity<Ingredient> getIngredientById(@PathVariable @NonNull Integer id) {
        return ResponseEntity.ok(inventoryService.getIngredientById(id));
    }

    @PostMapping("/ingredients")
    public ResponseEntity<Ingredient> createIngredient(@RequestBody Ingredient ingredient) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inventoryService.createIngredient(ingredient));
    }

    @PutMapping("/ingredients/{id}")
    public ResponseEntity<Ingredient> updateIngredient(@PathVariable @NonNull Integer id, @RequestBody @NonNull Ingredient ingredient) {
        return ResponseEntity.ok(inventoryService.updateIngredient(id, ingredient));
    }

    @DeleteMapping("/ingredients/{id}")
    public ResponseEntity<Map<String, String>> deleteIngredient(@PathVariable @NonNull Integer id) {
        inventoryService.deleteIngredient(id);
        return ResponseEntity.ok(Map.of("message", "Đã xóa nguyên liệu thành công"));
    }

    @PutMapping("/ingredients/{id}/quantity")
    public ResponseEntity<Ingredient> updateQuantity(@PathVariable @NonNull Integer id, @RequestBody Map<String, Object> payload) {
        BigDecimal amount = new BigDecimal(payload.get("amount").toString());
        String action = payload.get("action").toString();
        return ResponseEntity.ok(inventoryService.updateQuantity(id, amount, action));
    }

    @PostMapping("/ingredients/batch-deduct")
    public ResponseEntity<Void> deductStock(@RequestBody @NonNull List<InventoryDeductRequest> requests) {
        inventoryService.deductStock(requests);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/ingredients/low-stock")
    public ResponseEntity<List<Ingredient>> getLowStock() {
        return ResponseEntity.ok(inventoryService.getLowStock());
    }

    // Endpoint for Feign Client from menu-service
    @GetMapping("/ingredients/details")
    public ResponseEntity<Map<Integer, Map<String, String>>> getIngredientDetails(@RequestParam @NonNull List<Integer> ingredientIds) {
        return ResponseEntity.ok(inventoryService.getIngredientDetails(ingredientIds));
    }
}
