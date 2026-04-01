package com.restaurant.userservice.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class UserCreateRequest {
    @NotBlank(message = "Tên đăng nhập không được để trống")
    private String username;

    @NotBlank(message = "Mật khẩu không được để trống")
    private String password;

    @NotNull(message = "Vai trò không được để trống")
    private Integer roleId;

    private String fullName;
    private String phoneNumber;
    private Integer age;
    private String email;
    private String address;
}
