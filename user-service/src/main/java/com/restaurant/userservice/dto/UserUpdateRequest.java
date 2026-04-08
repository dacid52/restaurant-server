package com.restaurant.userservice.dto;

import lombok.Data;

@Data
public class UserUpdateRequest {
    private Integer roleId;
    private String fullName;
    private String phoneNumber;
    private Integer age;
    private String email;
    private String address;
    private String password;
    private String avatarUrl;
}
