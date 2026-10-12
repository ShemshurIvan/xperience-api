package com.xperience.dto.timeslot;

import com.xperience.entity.StatusSlot;
import com.xperience.entity.TimeSlot;

import java.time.LocalDateTime;

public record TimeSlotResponse(Long id,
                               LocalDateTime startTime,
                               LocalDateTime endTime,
                               Integer maxPeopleInSlot,
                               Long productId,
                               StatusSlot status,
                               Integer placesLeft
) {
    public static TimeSlotResponse from(TimeSlot timeSlot) {
        return new TimeSlotResponse(
                timeSlot.getId(),
                timeSlot.getStartTime(),
                timeSlot.getEndTime(),
                timeSlot.getMaxPeopleInSlot(),
                timeSlot.getProduct().getId(),
                timeSlot.getStatus(),
                timeSlot.getPlacesLeft()
        );
    }
}
