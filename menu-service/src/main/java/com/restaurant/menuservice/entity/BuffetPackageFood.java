package com.restaurant.menuservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "buffet_package_foods",
        uniqueConstraints = @UniqueConstraint(columnNames = {"buffet_package_id", "food_id"}))
@Getter
@Setter
public class BuffetPackageFood {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buffet_package_id", nullable = false)
    private BuffetPackage buffetPackage;

    @Column(name = "food_id", nullable = false)
    private Integer foodId;
}
