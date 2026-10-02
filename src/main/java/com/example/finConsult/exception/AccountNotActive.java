package com.example.finConsult.exception;

public class AccountNotActive extends RuntimeException {
    public AccountNotActive(String message) {
        super(message);
    }
}
