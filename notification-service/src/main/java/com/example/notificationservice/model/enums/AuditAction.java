package com.example.notificationservice.model.enums;

// every action the audit log knows, the same list the other services send. Only used to check the action filter of
// GET /admin/logs; the events are stored with whatever action they carry
public enum AuditAction {
    PROFILE_UPDATED,
    PROFILE_PICTURE_UPDATED,
    APPOINTMENT_BOOKED,
    APPOINTMENT_CONFIRMED,
    APPOINTMENT_CANCELLED,
    APPOINTMENT_CANCELLED_BY_ADMIN,
    APPOINTMENT_NO_SHOW,
    NO_SHOW_EXCUSED,
    USER_DEACTIVATED,
    ADVISOR_DEACTIVATED,
    KYC_SUBMITTED,
    KYC_APPROVED,
    KYC_REJECTED,
    ADVISOR_CREATED,
    CLIENT_BLACKLISTED,
    ADVISOR_APPLICATION_SUBMITTED,
    ADVISOR_APPLICATION_APPROVED,
    ADVISOR_APPLICATION_REJECTED,
    KYC_RESUBMITTED,
    CERTIFICATE_REPLACED
}
