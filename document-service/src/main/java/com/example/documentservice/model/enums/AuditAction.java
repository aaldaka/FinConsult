package com.example.documentservice.model.enums;

public enum AuditAction { // only the actions this service records, the audit log owns the full list
    KYC_SUBMITTED("KycDocument"),
    KYC_RESUBMITTED("KycDocument"),
    KYC_APPROVED("KycDocument"),
    KYC_REJECTED("KycDocument"),
    ADVISOR_APPLICATION_SUBMITTED("AdvisorApplication"),
    ADVISOR_APPLICATION_APPROVED("AdvisorApplication"),
    ADVISOR_APPLICATION_REJECTED("AdvisorApplication"),
    CERTIFICATE_REPLACED("ApplicationCertificate");

    private final String entityType; // the audit entry's entityType, see docs/events.md

    AuditAction(String entityType) {
        this.entityType = entityType;
    }

    public String entityType() {
        return entityType;
    }
}
