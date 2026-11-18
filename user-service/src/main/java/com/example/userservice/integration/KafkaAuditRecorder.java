package com.example.userservice.integration;

import com.example.userservice.model.enums.AuditAction;
import org.springframework.stereotype.Component;

// an AUDIT event on audit-events, keyed "<entityType>:<entityId>" so the entries for one record stay in order. The
// callers pass the entity type themselves (User, Advisor, Profile), the same values the original monolith stored
@Component
public class KafkaAuditRecorder implements AuditRecorder {

    static final String TOPIC = "audit-events";
    static final String TYPE = "AUDIT";

    private final EventPublisher events;

    public KafkaAuditRecorder(EventPublisher events) {
        this.events = events;
    }

    record AuditPayload(String action, String entityType, Long entityId, Long actorId, String details) {}

    @Override
    public void record(Long actorUserId, AuditAction action, String entityType, Long entityId, String details) {
        events.publish(TOPIC, entityType + ":" + entityId, TYPE,
                new AuditPayload(action.name(), entityType, entityId, actorUserId, details));
    }
}
