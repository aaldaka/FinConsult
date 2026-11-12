package com.example.bookingservice.integration;

import com.example.bookingservice.model.enums.AuditAction;

public interface AuditRecorder {

    void record(AuditAction action, long actorUserId, long entityId, String detail);
}
