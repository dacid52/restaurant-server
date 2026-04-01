package com.restaurant.userservice.service;

import com.restaurant.userservice.dto.UserCreateRequest;
import com.restaurant.userservice.dto.UserDto;
import com.restaurant.userservice.dto.UserUpdateRequest;
import com.restaurant.userservice.entity.Role;
import com.restaurant.userservice.entity.User;
import com.restaurant.userservice.repository.RoleRepository;
import com.restaurant.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.lang.NonNull;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Transactional(readOnly = true)
    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserDto getUserById(@NonNull Integer id) {
        return userRepository.findById(id).map(this::mapToDto)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));
    }

    @Transactional
    @SuppressWarnings("null")
    public UserDto createUser(UserCreateRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new RuntimeException("Tên đăng nhập đã tồn tại");
        }

        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new RuntimeException("Vai trò không hợp lệ"));

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(BCrypt.hashpw(request.getPassword(), BCrypt.gensalt(10)));
        user.setRole(role);
        user.setFullName(request.getFullName());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setAge(request.getAge());
        user.setEmail(request.getEmail());
        user.setAddress(request.getAddress());

        return mapToDto(userRepository.save(user));
    }

    @Transactional
    @SuppressWarnings("null")
    public UserDto updateUser(@NonNull Integer id, UserUpdateRequest request, Integer currentUserId, Integer currentUserRoleId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        if (!user.getId().equals(currentUserId) && currentUserId != 1 && currentUserRoleId != 1) {
            throw new RuntimeException("Không có quyền cập nhật thông tin người dùng khác");
        }

        if (request.getRoleId() != null && currentUserRoleId != 1) {
            throw new RuntimeException("Không có quyền cập nhật vai trò");
        }

        if (request.getRoleId() != null) {
            Role role = roleRepository.findById(request.getRoleId())
                    .orElseThrow(() -> new RuntimeException("Vai trò không hợp lệ"));
            user.setRole(role);
        }

        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getPhoneNumber() != null) user.setPhoneNumber(request.getPhoneNumber());
        if (request.getAge() != null) user.setAge(request.getAge());
        if (request.getEmail() != null) user.setEmail(request.getEmail());
        if (request.getAddress() != null) user.setAddress(request.getAddress());

        if (request.getPassword() != null) {
            user.setPassword(BCrypt.hashpw(request.getPassword(), BCrypt.gensalt(10)));
        }

        return mapToDto(userRepository.save(user));
    }

    @Transactional
    @SuppressWarnings("null")
    public void deleteUser(@NonNull Integer id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));
        
        // Cần thêm logic kiểm tra đơn hàng xem user có thể xoá không nếu cần
        // Tuy nhiên theo thiết kế, ta sẽ chỉ catch lỗi constraints ở DB thay vì query
        userRepository.delete(user);
    }

    @Transactional(readOnly = true)
    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    public UserDto mapToDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setRoleId(user.getRole().getId());
        dto.setRoleName(user.getRole().getName());
        dto.setFullName(user.getFullName());
        dto.setPhoneNumber(user.getPhoneNumber());
        dto.setAge(user.getAge());
        dto.setEmail(user.getEmail());
        dto.setAddress(user.getAddress());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setUpdatedAt(user.getUpdatedAt());
        return dto;
    }
}
