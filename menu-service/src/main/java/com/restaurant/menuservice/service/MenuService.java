package com.restaurant.menuservice.service;

import com.restaurant.menuservice.client.InventoryClient;
import com.restaurant.menuservice.dto.FoodCreateUpdateDto;
import com.restaurant.menuservice.dto.FoodDto;
import com.restaurant.menuservice.dto.IngredientDto;
import com.restaurant.menuservice.entity.Category;
import com.restaurant.menuservice.entity.Food;
import com.restaurant.menuservice.entity.FoodIngredient;
import com.restaurant.menuservice.repository.CategoryRepository;
import com.restaurant.menuservice.repository.FoodIngredientRepository;
import com.restaurant.menuservice.repository.FoodRepository;
import com.restaurant.menuservice.repository.BuffetPackageRepository;
import com.restaurant.menuservice.entity.BuffetPackage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.lang.NonNull;

@Service
@RequiredArgsConstructor
@Slf4j
public class MenuService {

    private final CategoryRepository categoryRepository;
    private final FoodRepository foodRepository;
    private final FoodIngredientRepository foodIngredientRepository;
    private final InventoryClient inventoryClient;
    private final BuffetPackageRepository buffetPackageRepository;

    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    public Category getCategoryById(@NonNull Integer id) {
        return categoryRepository.findById(id).orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục"));
    }

    @Transactional
    public Category createCategory(@NonNull Category category) {
        return categoryRepository.save(category);
    }

    @Transactional
    public Category updateCategory(@NonNull Integer id, @NonNull Category category) {
        Category existing = getCategoryById(id);
        existing.setName(category.getName());
        return categoryRepository.save(existing);
    }

    @Transactional
    public void deleteCategory(@NonNull Integer id) {
        if (foodRepository.countByCategoryId(id) > 0) {
            throw new RuntimeException("Không thể xóa danh mục có món ăn");
        }
        categoryRepository.deleteById(id);
    }

    public List<BuffetPackage> getBuffetPackages() {
        return buffetPackageRepository.findAll();
    }

    public List<FoodDto> getFoods(Integer categoryId) {
        List<Food> foods = categoryId != null ? foodRepository.findByCategoryId(categoryId) : foodRepository.findAll();
        return foods.stream().map(this::mapFoodToDto).collect(Collectors.toList());
    }

    public FoodDto getFoodById(@NonNull Integer id) {
        Food food = foodRepository.findById(id).orElseThrow(() -> new RuntimeException("Không tìm thấy món ăn"));
        return mapFoodToDto(food);
    }

    @Transactional
    @SuppressWarnings("null")
    public FoodDto createFood(FoodCreateUpdateDto dto) {
        Food food = new Food();
        food.setName(dto.getName());
        food.setPrice(dto.getPrice());
        food.setImageUrl(dto.getImage_url());

        if (dto.getCategory_id() != null) {
            food.setCategory(getCategoryById(dto.getCategory_id()));
        }

        Food savedFood = foodRepository.save(food);

        if (dto.getIngredients() != null && !dto.getIngredients().isEmpty()) {
            for (FoodCreateUpdateDto.IngredientInputDto input : dto.getIngredients()) {
                FoodIngredient fi = new FoodIngredient();
                fi.setFoodId(savedFood.getId());
                fi.setIngredientId(input.getId());
                fi.setAmount(input.getAmount());
                foodIngredientRepository.save(fi);
            }
        }
        return mapFoodToDto(savedFood);
    }

    @Transactional
    @SuppressWarnings("null")
    public FoodDto updateFood(@NonNull Integer id, FoodCreateUpdateDto dto) {
        Food food = foodRepository.findById(id).orElseThrow(() -> new RuntimeException("Không tìm thấy món ăn"));
        
        if (dto.getName() != null) food.setName(dto.getName());
        if (dto.getPrice() != null) food.setPrice(dto.getPrice());
        if (dto.getImage_url() != null) food.setImageUrl(dto.getImage_url());
        if (dto.getCategory_id() != null) food.setCategory(getCategoryById(dto.getCategory_id()));

        foodRepository.save(food);

        if (dto.getIngredients() != null) {
            foodIngredientRepository.deleteByFoodId(id);
            for (FoodCreateUpdateDto.IngredientInputDto input : dto.getIngredients()) {
                FoodIngredient fi = new FoodIngredient();
                fi.setFoodId(id);
                fi.setIngredientId(input.getId());
                fi.setAmount(input.getAmount());
                foodIngredientRepository.save(fi);
            }
        }

        return mapFoodToDto(food);
    }

    @Transactional
    public void deleteFood(@NonNull Integer id) {
        // Assume constraints checked externally
        foodIngredientRepository.deleteByFoodId(id);
        foodRepository.deleteById(id);
    }

    private FoodDto mapFoodToDto(Food food) {
        FoodDto dto = new FoodDto();
        dto.setId(food.getId());
        dto.setName(food.getName());
        dto.setPrice(food.getPrice());
        dto.setImageUrl(food.getImageUrl());
        if (food.getCategory() != null) {
            dto.setCategoryId(food.getCategory().getId());
            dto.setCategoryName(food.getCategory().getName());
        }

        List<FoodIngredient> fis = foodIngredientRepository.findByFoodId(food.getId());
        
        List<IngredientDto> ingredientDtos = new ArrayList<>();
        if (!fis.isEmpty()) {
            List<Integer> ids = fis.stream().map(FoodIngredient::getIngredientId).collect(Collectors.toList());
            Map<Integer, Map<String, String>> details = null;
            try {
                details = inventoryClient.getIngredientDetails(ids);
            } catch (Exception e) {
                log.warn("Lỗi gọi inventory service");
            }
            
            for (FoodIngredient fi : fis) {
                String name = "N/A";
                String unit = "N/A";
                if (details != null && details.containsKey(fi.getIngredientId())) {
                    name = details.get(fi.getIngredientId()).get("name");
                    unit = details.get(fi.getIngredientId()).get("unit");
                }
                ingredientDtos.add(new IngredientDto(fi.getIngredientId(), name, unit, fi.getAmount()));
            }
        }
        dto.setIngredients(ingredientDtos);
        return dto;
    }
}
