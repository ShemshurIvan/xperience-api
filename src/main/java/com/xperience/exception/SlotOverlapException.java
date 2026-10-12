package com.xperience.exception;

public class SlotOverlapException extends RuntimeException {
    public SlotOverlapException() {
        super("This slot overlaps an existing slot");
    }
}
