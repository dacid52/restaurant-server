package com.restaurant.userservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    /**
     * Email hoặc số điện thoại.
     * Email: phải chứa @ và là địa chỉ email hợp lệ.
     * Số điện thoại: chuỗi số (10-11 chữ số).
     */
    @NotBlank(message = "Email hoặc số điện thoại không được để trống")
    private String identifier;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 6, message = "Mật khẩu phải có ít nhất 6 ký tự")
    private String password;

    @NotBlank(message = "Họ tên không được để trống")
    private String fullName;
}
