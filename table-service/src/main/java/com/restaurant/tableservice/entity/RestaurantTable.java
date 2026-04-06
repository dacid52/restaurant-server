package com.restaurant.tableservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tables")
@Getter
@Setter
public class RestaurantTable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 50)
    private String status = "Trống";

    @Column(name = "is_buffet")
    private Boolean isBuffet = false;

    /** Sức chứa tối đa (số ghế). Null = chưa cấu hình. */
    @Column(name = "capacity")
    private Integer capacity;
}
