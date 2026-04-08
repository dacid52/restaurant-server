package com.restaurant.menuservice.service;

import com.restaurant.menuservice.client.InventoryClient;
import com.restaurant.menuservice.dto.BuffetPackageDto;
import com.restaurant.menuservice.dto.FoodCreateUpdateDto;
import com.restaurant.menuservice.dto.FoodDto;
import com.restaurant.menuservice.dto.IngredientDto;
import com.restaurant.menuservice.entity.BuffetPackage;
import com.restaurant.menuservice.entity.BuffetPackageFood;
import com.restaurant.menuservice.entity.Category;
import com.restaurant.menuservice.entity.Food;
import com.restaurant.menuservice.entity.FoodIngredient;
import com.restaurant.menuservice.repository.BuffetPackageFoodRepository;
import com.restaurant.menuservice.repository.BuffetPackageRepository;
import com.restaurant.menuservice.repository.CategoryRepository;
import com.restaurant.menuservice.repository.FoodIngredientRepository;
import com.restaurant.menuservice.repository.FoodRepository;
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
    private final BuffetPackageFoodRepository buffetPackageFoodRepository;

    // ─── Categories ─────────────────────────────────────────────────────

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

    // ─── Buffet Packages ────────────────────────────────────────────────

    public List<BuffetPackageDto> getBuffetPackages() {
        return buffetPackageRepository.findAll().stream()
                .map(this::toBuffetPackageDto)
                .collect(Collectors.toList());
    }

    public BuffetPackageDto getBuffetPackageById(@NonNull Integer id) {
        BuffetPackage pkg = buffetPackageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy gói buffet"));
        return toBuffetPackageDto(pkg);
    }

    @Transactional
    @SuppressWarnings("null")
    public BuffetPackageDto createBuffetPackage(@NonNull BuffetPackageDto dto) {
        BuffetPackage pkg = new BuffetPackage();
        pkg.setName(dto.getName());
        pkg.setPrice(dto.getPrice());
        pkg.setDescription(dto.getDescription());
        pkg.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        BuffetPackage saved = buffetPackageRepository.save(pkg);
        setPackageFoods(saved, dto.getFoodIds());
        return toBuffetPackageDto(saved);
    }

    @SuppressWarnings("null")
    @Transactional
    public BuffetPackageDto updateBuffetPackage(@NonNull Integer id, @NonNull BuffetPackageDto dto) {
        BuffetPackage pkg = buffetPackageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy gói buffet"));
        if (dto.getName() != null) pkg.setName(dto.getName());
        if (dto.getPrice() != null) pkg.setPrice(dto.getPrice());
        if (dto.getDescription() != null) pkg.setDescription(dto.getDescription());
        if (dto.getIsActive() != null) pkg.setIsActive(dto.getIsActive());
        buffetPackageRepository.save(pkg);
        if (dto.getFoodIds() != null) {
            setPackageFoods(pkg, dto.getFoodIds());
        }
        return toBuffetPackageDto(pkg);
    }

    @Transactional
    public void deleteBuffetPackage(@NonNull Integer id) {
        buffetPackageFoodRepository.deleteByBuffetPackageId(id);
        buffetPackageRepository.deleteById(id);
    }

    /** Trả về danh sách FoodDto thuộc gói buffet (dùng cho trang gọi món của khách) */
    public List<FoodDto> getFoodsByBuffetPackageId(@NonNull Integer packageId) {
        List<Integer> foodIds = buffetPackageFoodRepository.findFoodIdsByBuffetPackageId(packageId);
        if (foodIds.isEmpty()) return List.of();
        return foodRepository.findAllById(foodIds).stream()
                .map(this::mapFoodToDto)
                .collect(Collectors.toList());
    }

    private void setPackageFoods(BuffetPackage pkg, List<Integer> foodIds) {
        buffetPackageFoodRepository.deleteByBuffetPackageId(pkg.getId());
        if (foodIds == null || foodIds.isEmpty()) return;
        for (Integer foodId : foodIds) {
            BuffetPackageFood link = new BuffetPackageFood();
            link.setBuffetPackage(pkg);
            link.setFoodId(foodId);
            buffetPackageFoodRepository.save(link);
        }
    }

    private BuffetPackageDto toBuffetPackageDto(BuffetPackage pkg) {
        BuffetPackageDto dto = new BuffetPackageDto();
        dto.setId(pkg.getId());
        dto.setName(pkg.getName());
        dto.setPrice(pkg.getPrice());
        dto.setDescription(pkg.getDescription());
        dto.setIsActive(pkg.getIsActive());
        List<Integer> foodIds = buffetPackageFoodRepository.findFoodIdsByBuffetPackageId(pkg.getId());
        dto.setFoodIds(foodIds);
        if (!foodIds.isEmpty()) {
            dto.setFoods(foodRepository.findAllById(foodIds).stream()
                    .map(this::mapFoodToDto)
                    .collect(Collectors.toList()));
        } else {
            dto.setFoods(List.of());
        }
        return dto;
    }

    // ─── Foods ──────────────────────────────────────────────────────────

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
