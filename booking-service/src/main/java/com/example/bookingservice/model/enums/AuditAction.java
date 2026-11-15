package com.example.bookingservice.model.enums;

public enum AuditAction { // only the actions booking records, the audit log owns the full list
    APPOINTMENT_BOOKED("Appointment"),
    APPOINTMENT_CONFIRMED("Appointment"),
    APPOINTMENT_CANCELLED("Appointment"),
    APPOINTMENT_NO_SHOW("Appointment"),
    NO_SHOW_EXCUSED("Appointment"),
    CLIENT_BLACKLISTED("User");

    private final String entityType; // the audit entry's entityType, see docs/events.md

    AuditAction(String entityType) {
        this.entityType = entityType;
    }

    public String entityType() {
        return entityType;
    }
}
