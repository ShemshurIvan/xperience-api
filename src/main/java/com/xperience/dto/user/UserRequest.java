package com.xperience.dto.user;

import jakarta.validation.constraints.*;

public record UserRequest(@NotBlank @Size(max = 50) String firstName,
                          @NotBlank @Size(max = 50) String lastName,
                          @NotBlank @Email String email,
                          @NotBlank @Size(min = 8, max = 72) String password,
                          @NotNull @Min(18) @Max(116) Integer age,
                          AccountType accountType) {
}