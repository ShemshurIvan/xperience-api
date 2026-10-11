package com.xperience.dto.reservation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReservationRequest(@NotNull Long timeSlotId,
                                 @NotNull Long guestId,
                                 @NotNull @Min(1) Integer numberPeople
) {
}
