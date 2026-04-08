package com.restaurant.userservice.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserDto {
    private Integer id;
    private String username;
    private Integer roleId;
    private String roleName;
    private String fullName;
    private String phoneNumber;
    private Integer age;
    private String email;
    private String address;
    private String avatarUrl;
    private boolean emailVerified;
    private boolean banned;
    private String banReason;
    private LocalDateTime bannedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
