package com.xperience.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String email) {
        super("The user with this email: " + email + " was not found");
    }
    public UserNotFoundException(Long id){ super("The user with this id: " + id + " was not found");}
}
