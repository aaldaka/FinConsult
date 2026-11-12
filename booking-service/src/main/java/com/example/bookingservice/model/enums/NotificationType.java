package com.example.bookingservice.model.enums;

public enum NotificationType { // only the types booking sends, the notification service owns the full list
    APPOINTMENT_BOOKED,
    APPOINTMENT_CONFIRMED,
    APPOINTMENT_CANCELLED,
    APPOINTMENT_NO_SHOW // the monolith also uses this type for the late cancellation strike message
}
