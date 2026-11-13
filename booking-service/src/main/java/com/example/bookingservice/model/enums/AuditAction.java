package com.example.bookingservice.model.enums;

public enum AuditAction { // only the actions booking records, the audit log owns the full list
    APPOINTMENT_BOOKED,
    APPOINTMENT_CONFIRMED,
    APPOINTMENT_CANCELLED,
    APPOINTMENT_NO_SHOW,
    NO_SHOW_EXCUSED,
    CLIENT_BLACKLISTED
}
