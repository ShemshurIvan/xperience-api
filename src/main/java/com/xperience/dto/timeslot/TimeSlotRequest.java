package com.xperience.dto.timeslot;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record TimeSlotRequest(@NotNull @Future LocalDateTime startTime,
                              @NotNull @Min(1) Integer maxPeopleInSlot
) {
}
