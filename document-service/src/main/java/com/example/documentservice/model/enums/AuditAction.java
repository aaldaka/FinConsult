package com.example.documentservice.model.enums;

public enum AuditAction { // only the actions this service records, the audit log owns the full list
    KYC_SUBMITTED,
    KYC_RESUBMITTED,
    KYC_APPROVED,
    KYC_REJECTED,
    ADVISOR_APPLICATION_SUBMITTED,
    ADVISOR_APPLICATION_APPROVED,
    ADVISOR_APPLICATION_REJECTED,
    CERTIFICATE_REPLACED
}
