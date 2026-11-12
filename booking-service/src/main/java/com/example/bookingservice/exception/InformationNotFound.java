package com.example.bookingservice.exception;

public class InformationNotFound extends RuntimeException {
    public InformationNotFound(String message) {
        super(message);
    }
}
