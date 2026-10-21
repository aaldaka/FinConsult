package com.example.finConsult.model.enums;

public enum NotificationType {
    APPOINTMENT_BOOKED,
    APPOINTMENT_CONFIRMED,
    APPOINTMENT_CANCELLED,
    KYC_REVIEWED, // approved/ rejected the docs
    APPOINTMENT_NO_SHOW, //to alert client about strike counts
    ADVISOR_APPLICATION_REVIEWED // approved/rejected, outcome is in the message text
}