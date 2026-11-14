package com.example.documentservice.model.enums;

public enum NotificationType { // only the types this service sends, the notification service owns the full list
    KYC_REVIEWED, // approved or rejected, the outcome is in the message
    ADVISOR_APPLICATION_REVIEWED, // approved or rejected, the outcome is in the message
    DOCUMENT_FLAGGED
}
