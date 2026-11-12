package com.example.bookingservice.exception;

public class SlotUnavailable extends RuntimeException {
    public SlotUnavailable(String message) {
        super(message);
    }
}
