package com.xperience.dto.timeslot;

import com.xperience.entity.StatusSlot;

import java.time.LocalDateTime;

public record TimeSlotResponse(Long id,
                               LocalDateTime startTime,
                               LocalDateTime endTime,
                               Integer maxPeopleInSlot,
                               Long productId,
                               StatusSlot status,
                               Integer placesLeft
) {
}
