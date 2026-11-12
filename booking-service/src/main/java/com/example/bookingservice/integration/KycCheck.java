package com.example.bookingservice.integration;

import com.example.bookingservice.model.enums.DocumentType;

public interface KycCheck {

    // a null type means any approved document is enough
    boolean isApproved(long userId, DocumentType requiredType);
}
