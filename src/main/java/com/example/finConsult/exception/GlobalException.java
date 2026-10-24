package com.example.finConsult.exception;

import com.example.finConsult.model.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * Central exception-to-HTTP-response mapping. Every handler below turns a thrown
 * exception into the same {@link ErrorResponse} shape so API clients can rely on one
 * error contract regardless of which endpoint failed.
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

    @ExceptionHandler(EmailExists.class)
    public ResponseEntity<ErrorResponse> handleEmailExists(EmailExists e, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, e.getMessage(), request);
    }

    @ExceptionHandler(SlotUnavailable.class)
    public ResponseEntity<ErrorResponse> handleSlotUnavailable(SlotUnavailable e, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, e.getMessage(), request);
    }

    @ExceptionHandler(NotEligible.class)
    public ResponseEntity<ErrorResponse> handleNotEligible(NotEligible e, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, e.getMessage(), request);
    }

    @ExceptionHandler(InformationIncorrect.class)
    public ResponseEntity<ErrorResponse> handleInformationIncorrect(InformationIncorrect ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(InformationNotFound.class)
    public ResponseEntity<ErrorResponse> handleInformationNotFound(InformationNotFound e, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, e.getMessage(), request);
    }

    @ExceptionHandler(AccountNotActive.class)
    public ResponseEntity<ErrorResponse> handleAccountNotActive(AccountNotActive e, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, e.getMessage(), request);
    }

    @ExceptionHandler(AuthorizationDeniedException.class) // return proper code when hitting resrticted api
    public ResponseEntity<ErrorResponse> handleAuthorizationDenied(AuthorizationDeniedException e, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "You do not have permission to access this resource", request);
    }

    @ExceptionHandler(InvalidRequest.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidRequest e, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage(), request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException e, HttpServletRequest request) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "The file is too large", request); //413
    }

    @ExceptionHandler(MethodArgumentNotValidException.class) //  thrown with @valid
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Invalid request body", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class) // enums
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Invalid value for " + ex.getName(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException e, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "This conflicts with an existing record", request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimistic(ObjectOptimisticLockingFailureException e, HttpServletRequest request) {
        log.warn("Optimistic lock conflict on {}", request.getRequestURI());
        return build(HttpStatus.CONFLICT, "This record was changed by someone else. Please refresh and try again", request);
    }

    // Catch-all fallback for anything unexpected
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception e, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again.", request);
    }

}
