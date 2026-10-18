package com.xperience.exception.conflict;

public class ActiveBookingOnProductExistException extends RuntimeException {
    public ActiveBookingOnProductExistException(Long id) {
        super("Product 5 cannot be deleted: it has upcoming bookings");
    }
}
