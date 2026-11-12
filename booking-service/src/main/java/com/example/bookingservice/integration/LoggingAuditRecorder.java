package com.example.bookingservice.integration;

import com.example.bookingservice.model.enums.AuditAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// PLACEHOLDER: nothing is stored yet, the audit entry is only logged
@Slf4j
@Component
public class LoggingAuditRecorder implements AuditRecorder {

    @Override
    public void record(AuditAction action, long actorUserId, long entityId, String detail) {
        log.info("PLACEHOLDER audit {} by user {} on entity {}: {}", action, actorUserId, entityId, detail);
    }
}
