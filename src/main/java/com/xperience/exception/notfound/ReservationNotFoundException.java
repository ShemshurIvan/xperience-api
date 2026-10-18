package com.xperience.exception.notfound;

public class ReservationNotFoundException extends RuntimeException {
    public ReservationNotFoundException(Long reservationId) {
        super("Could not find reservation with id " + reservationId);
    }
}
