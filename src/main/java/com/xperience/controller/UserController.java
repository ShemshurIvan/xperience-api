package com.xperience.controller;

import com.xperience.dto.user.UserRequest;
import com.xperience.dto.user.UserResponse;
import com.xperience.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController("/api")
public class UserController {

    private final UserService userService;
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/createUser")
    public ResponseEntity<UserResponse> createUser(@RequestBody @Valid UserRequest userRequest) {
        UserResponse response = userService.createUser(userRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/updateUser")
    public ResponseEntity<UserResponse> updateUser(@AuthenticationPrincipal Jwt jwt,
                                                   @RequestBody @Valid UserRequest userRequest) {
        return ResponseEntity.ok(userService.updateUser(Long.valueOf(jwt.getSubject()), userRequest));
    }

    @GetMapping("/get/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @GetMapping("/get")
    public PagedModel<UserResponse> getAllUser(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return new PagedModel<>(userService.returnAllUsers(pageable));
    }

    @DeleteMapping("/deleteUser/{id}")
    public ResponseEntity<Void> deleteUserById(@AuthenticationPrincipal Jwt jwt, Authentication authentication,
                                               @PathVariable Long id) {
        userService.deleteUserById(Long.valueOf(jwt.getSubject()), isAdmin(authentication), id);
        return ResponseEntity.noContent().build();
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
