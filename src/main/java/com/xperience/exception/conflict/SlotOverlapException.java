package com.xperience.exception.conflict;

public class SlotOverlapException extends RuntimeException {
    public SlotOverlapException() {
        super("This slot overlaps an existing slot");
    }
}
