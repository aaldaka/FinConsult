package com.example.userservice.integration;

import com.example.userservice.model.enums.AuditAction;

/**
 * Writes an entry in the audit log, which belongs to another area. In the original monolith the entry was saved
 * in the same transaction. KafkaAuditRecorder sends it as an AUDIT event on audit-events, and notification-service stores it.
 */
public interface AuditRecorder {

    void record(Long actorUserId, AuditAction action, String entityType, Long entityId, String details);
}
