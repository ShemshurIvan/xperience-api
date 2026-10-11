package com.xperience.dto.cart;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CartItemRequest(@NotNull Long timeSlotId, @Min(1) @NotNull Integer numberPeople) {
}
