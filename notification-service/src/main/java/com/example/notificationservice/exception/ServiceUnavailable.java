package com.example.notificationservice.exception;

public class ServiceUnavailable extends RuntimeException {
    public ServiceUnavailable(String message) {
        super(message);
    }
}
