package com.restaurant.inventoryservice.service;

import com.restaurant.inventoryservice.entity.Ingredient;
import com.restaurant.inventoryservice.repository.IngredientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.lang.NonNull;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final IngredientRepository ingredientRepository;

    public List<Ingredient> getAllIngredients() {
        return ingredientRepository.findAllByOrderByNameAsc();
    }

    public Ingredient getIngredientById(@NonNull Integer id) {
        return ingredientRepository.findById(id).orElseThrow(() -> new RuntimeException("Không tìm thấy nguyên liệu"));
    }

    @Transactional
    public Ingredient createIngredient(Ingredient ingredient) {
        if (ingredient.getName() == null || ingredient.getUnit() == null || ingredient.getQuantity() == null) {
            throw new RuntimeException("Tên, đơn vị và số lượng là bắt buộc");
        }
        return ingredientRepository.save(ingredient);
    }

    @Transactional
    @SuppressWarnings("null")
    public Ingredient updateIngredient(@NonNull Integer id, @NonNull Ingredient details) {
        Ingredient existing = getIngredientById(id);
        if (details.getName() != null) existing.setName(details.getName());
        if (details.getUnit() != null) existing.setUnit(details.getUnit());
        if (details.getQuantity() != null) existing.setQuantity(details.getQuantity());
        return ingredientRepository.save(existing);
    }

    @Transactional
    public void deleteIngredient(@NonNull Integer id) {
        if (ingredientRepository.countUsageInMenu(id) > 0) {
            throw new RuntimeException("Không thể xóa nguyên liệu đang được sử dụng trong món ăn");
        }
        ingredientRepository.deleteById(id);
    }

    @Transactional
    public Ingredient updateQuantity(@NonNull Integer id, BigDecimal amount, String action) {
        Ingredient existing = getIngredientById(id);
        if ("add".equals(action)) {
            existing.setQuantity(existing.getQuantity().add(amount));
        } else if ("subtract".equals(action)) {
            BigDecimal newTemp = existing.getQuantity().subtract(amount);
            if (newTemp.compareTo(BigDecimal.ZERO) < 0) {
                throw new RuntimeException("Số lượng nguyên liệu không thể âm");
            }
            existing.setQuantity(newTemp);
        } else {
            throw new RuntimeException("Hành động phải là 'add' hoặc 'subtract'");
        }
        return ingredientRepository.save(existing);
    }

    public List<Ingredient> getLowStock() {
        return ingredientRepository.findByQuantityLessThanOrderByQuantityAsc(new BigDecimal("10"));
    }

    // specific method for menu-service feign client
    public Map<Integer, Map<String, String>> getIngredientDetails(@NonNull List<Integer> ids) {
        List<Ingredient> ingredients = ingredientRepository.findAllById(ids);
        Map<Integer, Map<String, String>> result = new HashMap<>();
        for (Ingredient i : ingredients) {
            Map<String, String> data = new HashMap<>();
            data.put("name", i.getName());
            data.put("unit", i.getUnit());
            result.put(i.getId(), data);
        }
        return result;
    }
}
