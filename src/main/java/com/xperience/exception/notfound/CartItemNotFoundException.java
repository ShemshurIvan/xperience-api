package com.xperience.exception.notfound;

public class CartItemNotFoundException extends RuntimeException {
    public CartItemNotFoundException(Long timeSlotId) {
        super("Time slot " + timeSlotId + " is not in the cart");
    }
}
