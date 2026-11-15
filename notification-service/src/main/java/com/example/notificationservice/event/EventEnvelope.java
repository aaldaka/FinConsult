package com.example.notificationservice.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.UUID;

// the envelope every message on the event topics has, see docs/events.md
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventEnvelope(UUID eventId, String type, OffsetDateTime occurredAt, String source, JsonNode payload) {}
