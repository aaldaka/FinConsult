package com.example.userservice.integration;

import com.example.userservice.model.enums.AuditAction;

/**
 * Writes an entry in the audit log, which belongs to another area. In the main app the entry was saved
 * in the same transaction. Until this is connected, the implementation only logs.
 */
public interface AuditRecorder {

    void record(Long actorUserId, AuditAction action, String entityType, Long entityId, String details);
}
