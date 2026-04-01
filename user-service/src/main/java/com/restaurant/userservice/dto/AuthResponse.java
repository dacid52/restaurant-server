package com.restaurant.userservice.dto;

import lombok.Data;
import lombok.AllArgsConstructor;

@Data
@AllArgsConstructor
public class AuthResponse {
    private UserDto user;
    private String token;
}
