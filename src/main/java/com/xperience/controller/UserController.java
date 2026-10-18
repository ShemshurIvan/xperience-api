package com.xperience.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.xperience.dto.user.UserRequest;
import com.xperience.dto.user.UserResponse;
import com.xperience.service.UserService;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Users", description = "Accounts and profiles")
@RestController
@RequestMapping("/api")
public class UserController {

    private final UserService userService;
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Update your own account")
    @PutMapping("/me/update")
    public ResponseEntity<UserResponse> updateUser(@AuthenticationPrincipal Jwt jwt,
                                                   @RequestBody @Valid UserRequest userRequest) {
        return ResponseEntity.ok(userService.updateUser(Long.valueOf(jwt.getSubject()), userRequest));
    }

    @Operation(summary = "Get one user")
    @GetMapping("/users/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @Operation(summary = "List all users",
               description = "Admins only. Paged.")
    @GetMapping("/users")
    public PagedModel<UserResponse> getAllUsers(@ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return new PagedModel<>(userService.returnAllUsers(pageable));
    }

    @Operation(summary = "Delete an account",
               description = "Your own account, or any account if you are an admin. Bookings you made are kept.")
    @DeleteMapping("/users/delete/{id}")
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
