package com.restaurant.userservice.service;

import com.restaurant.userservice.dto.AuthResponse;
import com.restaurant.userservice.dto.LoginRequest;
import com.restaurant.userservice.entity.User;
import com.restaurant.userservice.repository.UserRepository;
import com.restaurant.userservice.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final UserService userService;

    public AuthResponse login(LoginRequest request) {
        Optional<User> optionalUser = userRepository.findByUsername(request.getUsername());
        
        if (optionalUser.isEmpty()) {
            throw new RuntimeException("Tên đăng nhập hoặc mật khẩu không đúng");
        }

        User user = optionalUser.get();
        boolean isPasswordValid = false;

        if (user.getPassword().startsWith("$2")) {
            String dbHash = user.getPassword();
            if (dbHash.startsWith("$2b$")) {
                dbHash = "$2a$" + dbHash.substring(4);
            }
            isPasswordValid = BCrypt.checkpw(request.getPassword(), dbHash);
        } else {
            isPasswordValid = request.getPassword().equals(user.getPassword());
            if (isPasswordValid) {
                user.setPassword(BCrypt.hashpw(request.getPassword(), BCrypt.gensalt(10)));
                userRepository.save(user);
            }
        }

        if (!isPasswordValid) {
            throw new RuntimeException("Tên đăng nhập hoặc mật khẩu không đúng");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole().getId());

        return new AuthResponse(userService.mapToDto(user), token);
    }
}
