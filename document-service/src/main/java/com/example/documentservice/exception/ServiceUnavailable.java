package com.example.documentservice.exception;

public class ServiceUnavailable extends RuntimeException {
    public ServiceUnavailable(String message) {
        super(message);
    }
}
