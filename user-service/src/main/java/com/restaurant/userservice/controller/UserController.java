package com.restaurant.userservice.controller;

import com.restaurant.userservice.dto.UserCreateRequest;
import com.restaurant.userservice.dto.UserDto;
import com.restaurant.userservice.dto.UserUpdateRequest;
import com.restaurant.userservice.entity.Role;
import com.restaurant.userservice.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.lang.NonNull;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private static final String ADMIN_ROLE = "ADMIN";

    private final UserService userService;

    @GetMapping("/users")
    public ResponseEntity<List<UserDto>> getUsers(HttpServletRequest request) {
        String roleName = (String) request.getAttribute("roleName");
        if (!ADMIN_ROLE.equalsIgnoreCase(roleName)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<UserDto> getUserById(@PathVariable @NonNull Integer id, HttpServletRequest request) {
        String roleName = (String) request.getAttribute("roleName");
        if (!ADMIN_ROLE.equalsIgnoreCase(roleName)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PostMapping("/users")
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody UserCreateRequest createRequest, HttpServletRequest request) {
        String roleName = (String) request.getAttribute("roleName");
        if (!ADMIN_ROLE.equalsIgnoreCase(roleName)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(createRequest));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<UserDto> updateUser(@PathVariable @NonNull Integer id, @Valid @RequestBody UserUpdateRequest updateRequest, HttpServletRequest request) {
        Integer currentUserId = (Integer) request.getAttribute("userId");
        String roleName = (String) request.getAttribute("roleName");
        return ResponseEntity.ok(userService.updateUser(id, updateRequest, currentUserId, roleName));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable @NonNull Integer id, HttpServletRequest request) {
        String roleName = (String) request.getAttribute("roleName");
        if (!ADMIN_ROLE.equalsIgnoreCase(roleName)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        userService.deleteUser(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/roles")
    public ResponseEntity<List<Role>> getRoles(HttpServletRequest request) {
        String roleName = (String) request.getAttribute("roleName");
        if (!ADMIN_ROLE.equalsIgnoreCase(roleName)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(userService.getAllRoles());
    }

    @GetMapping("/users/roles")
    public ResponseEntity<List<Role>> getRolesAlt(HttpServletRequest request) {
        String roleName = (String) request.getAttribute("roleName");
        if (!ADMIN_ROLE.equalsIgnoreCase(roleName)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(userService.getAllRoles());
    }

    @GetMapping("/users/customers")
    public ResponseEntity<List<UserDto>> getCustomers(HttpServletRequest request) {
        String roleName = (String) request.getAttribute("roleName");
        if (!ADMIN_ROLE.equalsIgnoreCase(roleName)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(userService.getCustomers());
    }

    @PutMapping("/users/{id}/ban")
    public ResponseEntity<UserDto> banUser(
            @PathVariable @NonNull Integer id,
            @RequestBody(required = false) java.util.Map<String, String> body,
            HttpServletRequest request) {
        String roleName = (String) request.getAttribute("roleName");
        if (!ADMIN_ROLE.equalsIgnoreCase(roleName)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(userService.banUser(id, reason));
    }

    @PutMapping("/users/{id}/unban")
    public ResponseEntity<UserDto> unbanUser(@PathVariable @NonNull Integer id, HttpServletRequest request) {
        String roleName = (String) request.getAttribute("roleName");
        if (!ADMIN_ROLE.equalsIgnoreCase(roleName)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        return ResponseEntity.ok(userService.unbanUser(id));
    }
}
