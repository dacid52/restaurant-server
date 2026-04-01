package com.restaurant.tableservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "table_keys")
@Getter
@Setter
public class TableKey {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "table_id", nullable = false)
    private Integer tableId;

    @Column(name = "key_value", nullable = false, length = 100)
    private String keyValue;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "is_valid", nullable = false)
    private Boolean isValid = true;

    @Column(name = "device_session", length = 100)
    private String deviceSession;
}
