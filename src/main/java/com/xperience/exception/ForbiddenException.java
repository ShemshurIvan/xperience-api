package com.xperience.exception;

// The user is logged in, but isn't allowed to touch this resource (it belongs to someone else)
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}