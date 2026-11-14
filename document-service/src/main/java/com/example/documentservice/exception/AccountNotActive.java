package com.example.documentservice.exception;

public class AccountNotActive extends RuntimeException {
    public AccountNotActive(String message) {
        super(message);
    }
}
