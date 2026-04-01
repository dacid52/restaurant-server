package com.restaurant.menuservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "buffet_packages")
@Getter
@Setter
public class BuffetPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(precision = 18, scale = 2, nullable = false)
    private BigDecimal price;

    @Column(columnDefinition = "TEXT")
    private String description;
}
