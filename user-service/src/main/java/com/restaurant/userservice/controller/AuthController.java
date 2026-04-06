package com.restaurant.userservice.controller;

import com.restaurant.userservice.dto.AuthResponse;
import com.restaurant.userservice.dto.LoginRequest;
import com.restaurant.userservice.dto.RegisterRequest;
import com.restaurant.userservice.dto.UserDto;
import com.restaurant.userservice.service.AuthService;
import com.restaurant.userservice.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @Value("${app.frontend-url:http://localhost:3001}")
    private String frontendUrl;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.ok(Map.of(
                "message", "Đăng ký thành công. Vui lòng kiểm tra email và bấm link xác thực để kích hoạt tài khoản."
        ));
    }

    /**
     * Xác thực email. Được gọi khi customer bấm link trong email.
     * Sau khi xác thực thành công, redirect về trang login của Fe-Customer.
     */
    @GetMapping("/verify-email")
    public ResponseEntity<Map<String, String>> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(Map.of(
                "message", "Xác thực email thành công! Bạn có thể đăng nhập ngay bây giờ.",
                "redirect", frontendUrl + "/login?verified=true"
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser(HttpServletRequest request) {
        Integer userId = (Integer) request.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(userService.getUserById(userId));
    }
}
