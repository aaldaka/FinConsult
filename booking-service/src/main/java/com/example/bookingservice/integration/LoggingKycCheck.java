package com.example.bookingservice.integration;

import com.example.bookingservice.model.enums.DocumentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// PLACEHOLDER: the KYC service does not exist yet. It fails closed, so no booking that needs KYC passes by accident
@Slf4j
@Component
public class LoggingKycCheck implements KycCheck {

    @Override
    public boolean isApproved(long userId, DocumentType requiredType) {
        log.warn("PLACEHOLDER KYC check: refusing user {} (required document {}), no KYC service is connected yet",
                userId, requiredType == null ? "any" : requiredType);
        return false;
    }
}
