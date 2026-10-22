package com.example.finConsult.exception;

public class ObjectOptimisticLockingFailureException extends RuntimeException {
    public ObjectOptimisticLockingFailureException(String message) {
        super(message);
    }
}
