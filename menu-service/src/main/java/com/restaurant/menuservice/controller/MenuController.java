package com.restaurant.menuservice.controller;

import com.restaurant.menuservice.dto.BuffetPackageDto;
import com.restaurant.menuservice.dto.FoodCreateUpdateDto;
import com.restaurant.menuservice.dto.FoodDto;
import com.restaurant.menuservice.entity.Category;
import com.restaurant.menuservice.service.MenuService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.lang.NonNull;

@RestController
@RequestMapping("/api/menu")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    // Categories
    @GetMapping("/categories")
    public ResponseEntity<List<Category>> getCategories() {
        return ResponseEntity.ok(menuService.getAllCategories());
    }

    @GetMapping("/categories/{id}")
    public ResponseEntity<Category> getCategoryById(@PathVariable @NonNull Integer id) {
        return ResponseEntity.ok(menuService.getCategoryById(id));
    }

    @PostMapping("/categories")
    public ResponseEntity<Category> createCategory(@RequestBody Category category) {
        if (category.getName() == null) return ResponseEntity.badRequest().build();
        return ResponseEntity.status(HttpStatus.CREATED).body(menuService.createCategory(category));
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<Category> updateCategory(@PathVariable @NonNull Integer id, @RequestBody Category category) {
        if (category.getName() == null) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(menuService.updateCategory(id, category));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable @NonNull Integer id) {
        menuService.deleteCategory(id);
        return ResponseEntity.ok().build();
    }

    // Buffet Packages
    @GetMapping("/buffet-packages")
    public ResponseEntity<List<BuffetPackageDto>> getBuffetPackages() {
        return ResponseEntity.ok(menuService.getBuffetPackages());
    }

    @GetMapping("/buffet-packages/{id}")
    public ResponseEntity<BuffetPackageDto> getBuffetPackageById(@PathVariable @NonNull Integer id) {
        return ResponseEntity.ok(menuService.getBuffetPackageById(id));
    }

    @SuppressWarnings("null")
    @PostMapping("/buffet-packages")
    public ResponseEntity<BuffetPackageDto> createBuffetPackage(@RequestBody BuffetPackageDto dto, HttpServletRequest request) {
        if (!isAdminOrManager(request)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.status(HttpStatus.CREATED).body(menuService.createBuffetPackage(dto));
    }

    @SuppressWarnings("null")
    @PutMapping("/buffet-packages/{id}")
    public ResponseEntity<BuffetPackageDto> updateBuffetPackage(@PathVariable @NonNull Integer id, @RequestBody BuffetPackageDto dto, HttpServletRequest request) {
        if (!isAdminOrManager(request)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(menuService.updateBuffetPackage(id, dto));
    }

    @DeleteMapping("/buffet-packages/{id}")
    public ResponseEntity<Void> deleteBuffetPackage(@PathVariable @NonNull Integer id, HttpServletRequest request) {
        if (!isAdminOrManager(request)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        menuService.deleteBuffetPackage(id);
        return ResponseEntity.ok().build();
    }

    /** Chỉ cho phép ADMIN và MANAGER thao tác với gói buffet */
    private boolean isAdminOrManager(HttpServletRequest request) {
        String role = (String) request.getAttribute("roleName");
        return "ADMIN".equalsIgnoreCase(role) || "MANAGER".equalsIgnoreCase(role);
    }

    // Foods
    @GetMapping("/foods")
    public ResponseEntity<List<FoodDto>> getFoods(@RequestParam(required = false) Integer category_id) {
        return ResponseEntity.ok(menuService.getFoods(category_id));
    }

    @GetMapping("/foods/{id}")
    public ResponseEntity<FoodDto> getFoodById(@PathVariable @NonNull Integer id) {
        return ResponseEntity.ok(menuService.getFoodById(id));
    }

    @PostMapping("/foods")
    public ResponseEntity<FoodDto> createFood(@Valid @RequestBody FoodCreateUpdateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(menuService.createFood(dto));
    }

    @PutMapping("/foods/{id}")
    public ResponseEntity<FoodDto> updateFood(@PathVariable @NonNull Integer id, @Valid @RequestBody FoodCreateUpdateDto dto) {
        return ResponseEntity.ok(menuService.updateFood(id, dto));
    }

    @DeleteMapping("/foods/{id}")
    public ResponseEntity<Void> deleteFood(@PathVariable @NonNull Integer id) {
        menuService.deleteFood(id);
        return ResponseEntity.ok().build();
    }
}
