package com.xperience.exception.conflict;

public class ActiveBookingsExistException extends RuntimeException {

    public ActiveBookingsExistException(Long userId) {
        super("User with id " + userId + " cannot be deleted: other guests have upcoming bookings on their experiences");
    }

}