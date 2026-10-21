package com.example.finConsult.exception;

public class SlotUnavailable extends RuntimeException {
    public SlotUnavailable(String message) {
        super(message);
    }
}
