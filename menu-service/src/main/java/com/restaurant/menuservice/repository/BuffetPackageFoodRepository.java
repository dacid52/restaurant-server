package com.restaurant.menuservice.repository;

import com.restaurant.menuservice.entity.BuffetPackageFood;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BuffetPackageFoodRepository extends JpaRepository<BuffetPackageFood, Integer> {

    List<BuffetPackageFood> findByBuffetPackageId(Integer buffetPackageId);

    @Modifying
    @Query("DELETE FROM BuffetPackageFood f WHERE f.buffetPackage.id = :packageId")
    void deleteByBuffetPackageId(@Param("packageId") Integer packageId);

    @Query("SELECT f.foodId FROM BuffetPackageFood f WHERE f.buffetPackage.id = :packageId")
    List<Integer> findFoodIdsByBuffetPackageId(@Param("packageId") Integer packageId);
}
