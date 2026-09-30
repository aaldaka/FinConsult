package com.example.finConsult.exception;

public class EmailExists extends RuntimeException {
    public EmailExists(String message) {
        super(message);
    }
}
