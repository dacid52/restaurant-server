package com.restaurant.userservice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class LoginRequest {
    /**
     * Chấp nhận email, số điện thoại, hoặc username.
     * Backward compatible: client cũ gửi JSON field "username" vẫn hoạt động.
     */
    @JsonAlias("username")
    @NotBlank(message = "Tên đăng nhập không được để trống")
    private String identifier;

    @NotBlank(message = "Mật khẩu không được để trống")
    private String password;
}
