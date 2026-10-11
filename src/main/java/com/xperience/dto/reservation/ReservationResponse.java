package com.xperience.dto.reservation;

import com.xperience.entity.ReservationStatus;

import java.math.BigDecimal;

public record ReservationResponse(Long id,
                                  Integer numberPeople,
                                  BigDecimal pricePaid,
                                  Long productId,
                                  Long guestId,
                                  ReservationStatus status,
                                  Long timeSlotId) {
}
