package com.example.finConsult.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@RestControllerAdvice
public class GlobalException { // adds meta data like http codes to the existing exceptions

    @ExceptionHandler(EmailExists.class)
    public ResponseEntity<Map<String, String>> handleEmailExists(EmailExists e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(InformationIncorrect.class)
    public ResponseEntity<Map<String, String>> handleInformationIncorrect(InformationIncorrect ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(InformationNotFound.class)
    public ResponseEntity<Map<String, String>> handleInformationNotFound(InformationNotFound e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
    }

    // Catch-all fallback for anything unexpected
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneric(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Something went wrong. Please try again."));
    }
}