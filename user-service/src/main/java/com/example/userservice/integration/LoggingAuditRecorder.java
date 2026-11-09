package com.example.userservice.integration;

import com.example.userservice.model.enums.AuditAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Placeholder until the audit log is connected: logs the entry and saves nothing. */
@Slf4j
@Component
public class LoggingAuditRecorder implements AuditRecorder {

    @Override
    public void record(Long actorUserId, AuditAction action, String entityType, Long entityId, String details) {
        log.info("[not connected] audit entry: actor={} action={} {}#{} {}", actorUserId, action, entityType, entityId, details);
    }
}
