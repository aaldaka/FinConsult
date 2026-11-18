package com.example.userservice.model.enums;

/**
 * The audit actions this service raises. The audit log itself belongs to another area, so only the
 * names used by the user area are here. They are the same names notification-service stores in audit_logs.action.
 */
public enum AuditAction {
    PROFILE_UPDATED,
    PROFILE_PICTURE_UPDATED,
    USER_DEACTIVATED,
    ADVISOR_DEACTIVATED,
    ADVISOR_CREATED
}
