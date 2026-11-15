package com.example.bookingservice.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

// wraps a payload in the envelope from docs/events.md and sends it. The callers run after the commit and the change
// is already saved, so a failure is only logged with the event id and topic, nothing is thrown back to them
@Slf4j
@Component
public class EventPublisher {

    static final String SOURCE = "booking-service";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public EventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    record EventEnvelope(UUID eventId, String type, OffsetDateTime occurredAt, String source, Object payload) {}

    public void publish(String topic, String key, String type, Object payload) {
        UUID eventId = UUID.randomUUID();
        try {
            String json = objectMapper.writeValueAsString(
                    new EventEnvelope(eventId, type, OffsetDateTime.now(ZoneOffset.UTC), SOURCE, payload));
            kafkaTemplate.send(topic, key, json).whenComplete((result, e) -> {
                if (e != null) {
                    log.error("Could not send event {} ({}) to {}", eventId, type, topic, e);
                }
            });
        } catch (RuntimeException e) { // building the json, or send() giving up waiting for the broker
            log.error("Could not send event {} ({}) to {}", eventId, type, topic, e);
        }
    }
}
