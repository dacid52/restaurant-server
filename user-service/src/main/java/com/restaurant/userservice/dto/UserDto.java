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
    private boolean emailVerified;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
