package com.example.notificationservice.exception;

import com.example.notificationservice.model.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;

/**
 * Maps exceptions to HTTP responses, all in the same {@link ErrorResponse} shape as the main app.
 */
@Slf4j
@RestControllerAdvice
public class GlobalException { // adds meta data like http codes to the existing exceptions

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        if (status.is4xxClientError()) {
            log.warn("{} {} -> {}: {}", request.getMethod(), request.getRequestURI(), status.value(), message);
        }
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                status.name(),
                message,
                request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(InformationNotFound.class)
    public ResponseEntity<ErrorResponse> handleInformationNotFound(InformationNotFound e, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, e.getMessage(), request);
    }

    @ExceptionHandler(InvalidRequest.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidRequest e, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage(), request);
    }

    @ExceptionHandler(ServiceUnavailable.class) // the user service did not answer or answered with an error
    public ResponseEntity<ErrorResponse> handleServiceUnavailable(ServiceUnavailable e, HttpServletRequest request) {
        log.warn("{} {} -> 503: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class) // for example /notifications/abc/read
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Invalid value for " + ex.getName(), request);
    }

    // without these two the catch-all below would turn an unknown url or a wrong method into a 500
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "No such endpoint", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, e.getMessage(), request);
    }

    // Catch-all fallback for anything unexpected
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception e, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again.", request);
    }

}
