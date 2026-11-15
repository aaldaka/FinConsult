package com.example.bookingservice.integration;

import com.example.bookingservice.model.enums.AuditAction;
import com.example.bookingservice.model.enums.NotificationType;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// no broker: the KafkaTemplate is a mock that records what was sent, and the json is read back to check it against
// docs/events.md
class EventPublisherTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);

    private EventPublisher events;
    private KafkaNotificationPublisher notifications;
    private KafkaAuditRecorder audit;

    @BeforeEach
    void setUp() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        events = new EventPublisher(kafkaTemplate, objectMapper);
        notifications = new KafkaNotificationPublisher(events);
        audit = new KafkaAuditRecorder(events);
    }

    private record Sent(String topic, String key, JsonNode event) {}

    private List<Sent> sent(int count) {
        ArgumentCaptor<String> topics = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> values = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, times(count)).send(topics.capture(), keys.capture(), values.capture());
        return IntStream.range(0, count)
                .mapToObj(i -> new Sent(topics.getAllValues().get(i), keys.getAllValues().get(i), objectMapper.readTree(values.getAllValues().get(i))))
                .toList();
    }

    private Set<String> fieldNames(JsonNode node) {
        return Set.copyOf(node.propertyNames());
    }

    // the envelope

    @Test
    void theEnvelopeHasTheFiveFieldsOfTheContract() {
        events.publish("notification-events", "14", "NOTIFICATION", new KafkaNotificationPublisher.NotificationPayload(14, "APPOINTMENT_BOOKED", "x", 42L));

        JsonNode event = sent(1).get(0).event();
        assertEquals(Set.of("eventId", "type", "occurredAt", "source", "payload"), fieldNames(event));
        assertDoesNotThrow(() -> UUID.fromString(event.get("eventId").asString()));
        assertEquals("NOTIFICATION", event.get("type").asString());
        assertTrue(event.get("occurredAt").isString(), "occurredAt is an ISO-8601 string, not a number");
        assertDoesNotThrow(() -> OffsetDateTime.parse(event.get("occurredAt").asString()));
        assertEquals("booking-service", event.get("source").asString());
        assertTrue(event.get("payload").isObject());
    }

    @Test
    void everyEventGetsItsOwnEventId() {
        notifications.publish(14, NotificationType.APPOINTMENT_BOOKED, "x", 42);
        notifications.publish(14, NotificationType.APPOINTMENT_BOOKED, "x", 42);

        List<Sent> sent = sent(2);
        assertNotEquals(sent.get(0).event().get("eventId").asString(), sent.get(1).event().get("eventId").asString());
    }

    // NOTIFICATION

    @Test
    void aNotificationGoesToNotificationEventsKeyedByTheRecipient() {
        notifications.publish(14, NotificationType.APPOINTMENT_CONFIRMED, "Reem confirmed your appointment.", 42);

        Sent sent = sent(1).get(0);
        assertEquals("notification-events", sent.topic());
        assertEquals("14", sent.key());
        assertEquals("NOTIFICATION", sent.event().get("type").asString());
        JsonNode payload = sent.event().get("payload");
        assertEquals(Set.of("userId", "notificationType", "message", "relatedAppointmentId"), fieldNames(payload));
        assertEquals(14, payload.get("userId").asLong());
        assertEquals("APPOINTMENT_CONFIRMED", payload.get("notificationType").asString());
        assertEquals("Reem confirmed your appointment.", payload.get("message").asString());
        assertEquals(42, payload.get("relatedAppointmentId").asLong());
    }

    // AUDIT

    @Test
    void anAppointmentAuditGoesToAuditEventsKeyedByEntityTypeAndId() {
        audit.record(AuditAction.APPOINTMENT_CANCELLED, 10, 42, "Cancelled by Client");

        Sent sent = sent(1).get(0);
        assertEquals("audit-events", sent.topic());
        assertEquals("Appointment:42", sent.key());
        assertEquals("AUDIT", sent.event().get("type").asString());
        JsonNode payload = sent.event().get("payload");
        assertEquals(Set.of("action", "entityType", "entityId", "actorId", "details"), fieldNames(payload));
        assertEquals("APPOINTMENT_CANCELLED", payload.get("action").asString());
        assertEquals("Appointment", payload.get("entityType").asString());
        assertEquals(42, payload.get("entityId").asLong());
        assertEquals(10, payload.get("actorId").asLong());
        assertEquals("Cancelled by Client", payload.get("details").asString());
    }

    @Test
    void aBlacklistAuditIsAboutTheUser() {
        audit.record(AuditAction.CLIENT_BLACKLISTED, 10, 10, "blacklisted after reaching 3 strikes");

        Sent sent = sent(1).get(0);
        assertEquals("User:10", sent.key());
        assertEquals("User", sent.event().get("payload").get("entityType").asString());
        assertEquals("CLIENT_BLACKLISTED", sent.event().get("payload").get("action").asString());
    }

    @Test
    void everyAuditActionHasAnEntityTypeFromTheContract() {
        for (AuditAction action : AuditAction.values()) {
            assertTrue(Set.of("Appointment", "User").contains(action.entityType()), action.name());
        }
    }

    // failures never reach the caller

    @Test
    void aSendThatFailsLaterIsOnlyLogged() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("delivery timed out")));

        assertDoesNotThrow(() -> notifications.publish(14, NotificationType.APPOINTMENT_BOOKED, "x", 42));
        assertDoesNotThrow(() -> audit.record(AuditAction.APPOINTMENT_BOOKED, 10, 42, "x"));
    }

    @Test
    void aSendThatThrowsStraightAwayIsOnlyLogged() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new KafkaException("broker not available"));

        assertDoesNotThrow(() -> notifications.publish(14, NotificationType.APPOINTMENT_BOOKED, "x", 42));
        assertDoesNotThrow(() -> audit.record(AuditAction.APPOINTMENT_BOOKED, 10, 42, "x"));
    }
}
