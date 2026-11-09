package com.example.userservice.exception;

public class AccountNotActive extends RuntimeException {
    public AccountNotActive(String message) {
        super(message);
    }
}
