package com.example.userservice.exception;

public class NotEligible extends RuntimeException {
    public NotEligible(String message) {
        super(message);
    }
}
