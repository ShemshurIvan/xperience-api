package com.xperience.dto.reservation;

import com.xperience.entity.Reservation;
import com.xperience.entity.ReservationStatus;

import java.math.BigDecimal;

public record ReservationResponse(Long id,
                                  Integer numberPeople,
                                  BigDecimal pricePaid,
                                  Long productId,
                                  Long guestId,
                                  ReservationStatus status,
                                  Long timeSlotId) {

    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getNumberPeople(),
                reservation.getPricePaid(),
                reservation.getTimeSlot().getProduct().getId(),
                reservation.getGuest() == null ? null : reservation.getGuest().getId(),
                reservation.getStatus(),
                reservation.getTimeSlot().getId()
        );
    }
}