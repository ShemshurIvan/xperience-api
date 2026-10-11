package com.xperience.dto.product;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ProductRequest(@NotBlank @Size(max = 100) String name,
                             @Size(max = 2000) String description,
                             @DecimalMin("0.00") @NotNull BigDecimal price,
                             @NotNull @Min(1) Integer maxPeople
) {}
