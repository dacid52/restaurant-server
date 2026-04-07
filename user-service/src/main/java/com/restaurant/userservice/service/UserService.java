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
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Transactional(readOnly = true)
    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserDto getUserById(@NonNull Integer id) {
        return userRepository.findById(id).map(this::mapToDto)
                .orElseThrow(() -> new RuntimeException("Khong tim thay nguoi dung"));
    }

    @Transactional
    @SuppressWarnings("null")
    public UserDto createUser(UserCreateRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new RuntimeException("Ten dang nhap da ton tai");
        }

        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new RuntimeException("Vai tro khong hop le"));

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
    public UserDto updateUser(@NonNull Integer id, UserUpdateRequest request, Integer currentUserId, String currentUserRoleName) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Khong tim thay nguoi dung"));

        boolean isAdmin = ADMIN_ROLE.equalsIgnoreCase(currentUserRoleName);
        if (!user.getId().equals(currentUserId) && !isAdmin) {
            throw new RuntimeException("Khong co quyen cap nhat thong tin nguoi dung khac");
        }

        if (request.getRoleId() != null && !isAdmin) {
            throw new RuntimeException("Khong co quyen cap nhat vai tro");
        }

        if (request.getRoleId() != null) {
            Role role = roleRepository.findById(request.getRoleId())
                    .orElseThrow(() -> new RuntimeException("Vai tro khong hop le"));
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
                .orElseThrow(() -> new RuntimeException("Khong tim thay nguoi dung"));
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
        dto.setEmailVerified(user.isEmailVerified());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setUpdatedAt(user.getUpdatedAt());
        return dto;
    }
}
