package com.example.finConsult.exception;

public class InvalidRequest extends RuntimeException {
    public InvalidRequest(String message) {
        super(message);
    }
}
