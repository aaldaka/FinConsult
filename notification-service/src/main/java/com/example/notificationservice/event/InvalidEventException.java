package com.example.notificationservice.event;

// the message is not valid json or breaks the contract (a field missing, an unknown notification type). Retrying would
// fail the same way, so the error handler sends it straight to the dead letter topic
public class InvalidEventException extends RuntimeException {
    public InvalidEventException(String message) {
        super(message);
    }

    public InvalidEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
