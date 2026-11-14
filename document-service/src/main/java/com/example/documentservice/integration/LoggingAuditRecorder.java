package com.example.documentservice.integration;

import com.example.documentservice.model.enums.AuditAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// PLACEHOLDER: nothing is written to the audit log yet, the entry is only logged
@Slf4j
@Component
public class LoggingAuditRecorder implements AuditRecorder {

    @Override
    public void record(AuditAction action, long actorUserId, long entityId, String detail) {
        log.info("PLACEHOLDER audit {} by user {} on {}: {}", action, actorUserId, entityId, detail);
    }
}
