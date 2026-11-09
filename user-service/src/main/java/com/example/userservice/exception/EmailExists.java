package com.example.userservice.exception;

public class EmailExists extends RuntimeException {
    public EmailExists(String message) {
        super(message);
    }
}
