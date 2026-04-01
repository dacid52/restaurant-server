package com.restaurant.tableservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "restaurant_layouts")
@Getter
@Setter
public class RestaurantLayout {
    @Id
    @Column(length = 50)
    private String id;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String layoutData;
}
