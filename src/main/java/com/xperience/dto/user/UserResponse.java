package com.xperience.dto.user;

import com.xperience.entity.Role;
import com.xperience.entity.User;

public record UserResponse(Long id, String firstName, String lastName, String email, Integer age, Role role) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getAge(),
                user.getRole()
        );
    }
}