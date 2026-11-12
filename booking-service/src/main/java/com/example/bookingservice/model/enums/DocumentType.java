package com.example.bookingservice.model.enums;

public enum DocumentType { //general, insurance and retirement dont need kyc. Moving money requires this unlike plain advice
    NATIONAL_ID,
    PASSPORT,
    PROOF_OF_ADDRESS,
    INCOME_PROOF
}
