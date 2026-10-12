package com.xperience.exception;

public class TimeSlotNotFoundException extends RuntimeException {
  public TimeSlotNotFoundException(Long id) {
    super("TimeSlot not found with id " + id);
  }
}
