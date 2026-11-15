package com.example.bookingservice.integration;

import com.example.bookingservice.model.enums.AuditAction;
import org.springframework.stereotype.Component;

// an AUDIT event on audit-events, keyed "<entityType>:<entityId>" so the entries for one record stay in order
@Component
public class KafkaAuditRecorder implements AuditRecorder {

    static final String TOPIC = "audit-events";
    static final String TYPE = "AUDIT";

    private final EventPublisher events;

    public KafkaAuditRecorder(EventPublisher events) {
        this.events = events;
    }

    record AuditPayload(String action, String entityType, long entityId, Long actorId, String details) {}

    @Override
    public void record(AuditAction action, long actorUserId, long entityId, String detail) {
        String entityType = action.entityType();
        events.publish(TOPIC, entityType + ":" + entityId, TYPE,
                new AuditPayload(action.name(), entityType, entityId, actorUserId, detail));
    }
}
