package com.example.userservice.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

// wraps a payload in the envelope from docs/events.md and sends it. The callers here run inside their transaction, so
// the send waits until it has committed (a rollback sends nothing); outside a transaction it is sent straight away.
// The change is already saved by then, so a failure is only logged with the event id, type and topic, never the
// payload (an email event carries a link), and nothing is thrown back to the caller
@Slf4j
@Component
public class EventPublisher {

    static final String SOURCE = "user-service";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public EventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    record EventEnvelope(UUID eventId, String type, OffsetDateTime occurredAt, String source, Object payload) {}

    public void publish(String topic, String key, String type, Object payload) {
        EventEnvelope event = new EventEnvelope(UUID.randomUUID(), type, OffsetDateTime.now(ZoneOffset.UTC), SOURCE, payload);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send(topic, key, event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send(topic, key, event);
            }
        });
    }

    private void send(String topic, String key, EventEnvelope event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, key, json).whenComplete((result, e) -> {
                if (e != null) {
                    log.error("Could not send event {} ({}) to {}: {}", event.eventId(), event.type(), topic, e.getClass().getSimpleName());
                }
            });
        } catch (RuntimeException e) { // building the json, or send() giving up waiting for the broker
            log.error("Could not send event {} ({}) to {}: {}", event.eventId(), event.type(), topic, e.getClass().getSimpleName());
        }
    }
}
