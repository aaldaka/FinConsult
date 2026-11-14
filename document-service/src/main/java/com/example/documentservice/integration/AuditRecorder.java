package com.example.documentservice.integration;

import com.example.documentservice.model.enums.AuditAction;

public interface AuditRecorder {

    void record(AuditAction action, long actorUserId, long entityId, String detail);
}
