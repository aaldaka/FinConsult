package com.example.notificationservice.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// the payload of an AUDIT event, see docs/events.md
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuditPayload(String action, String entityType, Long entityId, Long actorId, String details) {
}
