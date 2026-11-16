package com.example.documentservice.integration;

import com.example.documentservice.model.enums.AuditAction;
import com.example.documentservice.model.enums.NotificationType;
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
import java.util.Map;
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

    private KafkaNotificationPublisher notifications;
    private KafkaCheckEventPublisher checkEvents;
    private KafkaAuditRecorder audit;

    @BeforeEach
    void setUp() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        EventPublisher events = new EventPublisher(kafkaTemplate, objectMapper);
        notifications = new KafkaNotificationPublisher(events);
        checkEvents = new KafkaCheckEventPublisher(events);
        audit = new KafkaAuditRecorder(events);
    }

    private record Sent(String topic, String key, JsonNode event) {
        JsonNode payload() {
            return event.get("payload");
        }
    }

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
    void theEnvelopeHasTheFiveFieldsOfTheContractAndANewEventIdEachTime() {
        notifications.publish(14, NotificationType.KYC_REVIEWED, "x");
        notifications.publish(14, NotificationType.KYC_REVIEWED, "x");

        List<Sent> sent = sent(2);
        JsonNode event = sent.get(0).event();
        assertEquals(Set.of("eventId", "type", "occurredAt", "source", "payload"), fieldNames(event));
        assertDoesNotThrow(() -> UUID.fromString(event.get("eventId").asString()));
        assertTrue(event.get("occurredAt").isString(), "occurredAt is an ISO-8601 string, not a number");
        assertDoesNotThrow(() -> OffsetDateTime.parse(event.get("occurredAt").asString()));
        assertEquals("document-service", event.get("source").asString());
        assertNotEquals(event.get("eventId").asString(), sent.get(1).event().get("eventId").asString());
    }

    // NOTIFICATION

    @Test
    void aNotificationGoesToNotificationEventsKeyedByTheRecipientWithNoAppointment() {
        notifications.publish(14, NotificationType.DOCUMENT_FLAGGED, "2 KYC document(s) from Person 14 were flagged.");

        Sent sent = sent(1).get(0);
        assertEquals("notification-events", sent.topic());
        assertEquals("14", sent.key());
        assertEquals("NOTIFICATION", sent.event().get("type").asString());
        assertEquals(Set.of("userId", "notificationType", "message", "relatedAppointmentId"), fieldNames(sent.payload()));
        assertEquals(14, sent.payload().get("userId").asLong());
        assertEquals("DOCUMENT_FLAGGED", sent.payload().get("notificationType").asString());
        assertEquals("2 KYC document(s) from Person 14 were flagged.", sent.payload().get("message").asString());
        assertTrue(sent.payload().get("relatedAppointmentId").isNull());
    }

    // STREAM_PUSH

    @Test
    void aKycCheckedPushCarriesTheDocumentIdsInData() {
        checkEvents.push(14, Map.of("type", "KYC_CHECKED", "documentIds", List.of(31L, 32L)));

        Sent sent = sent(1).get(0);
        assertEquals("notification-events", sent.topic());
        assertEquals("14", sent.key());
        assertEquals("STREAM_PUSH", sent.event().get("type").asString());
        assertEquals(Set.of("userId", "eventName", "data"), fieldNames(sent.payload()));
        assertEquals(14, sent.payload().get("userId").asLong());
        assertEquals("KYC_CHECKED", sent.payload().get("eventName").asString());
        assertEquals(Set.of("documentIds"), fieldNames(sent.payload().get("data")));
        assertEquals(31, sent.payload().get("data").get("documentIds").get(0).asLong());
        assertEquals(32, sent.payload().get("data").get("documentIds").get(1).asLong());
    }

    @Test
    void aCertificatesCheckedPushCarriesTheApplicationIdAndAPushWithoutFieldsHasNoData() {
        checkEvents.push(14, Map.of("type", "CERTIFICATES_CHECKED", "applicationId", 7L));
        checkEvents.push(14, Map.of("type", "CERTIFICATES_CHECKED"));

        List<Sent> sent = sent(2);
        assertEquals("CERTIFICATES_CHECKED", sent.get(0).payload().get("eventName").asString());
        assertEquals(7, sent.get(0).payload().get("data").get("applicationId").asLong());
        assertTrue(sent.get(1).payload().get("data").isNull());
    }

    // AUDIT

    @Test
    void anAuditGoesToAuditEventsKeyedByEntityTypeAndIdWithEveryField() {
        audit.record(AuditAction.KYC_APPROVED, 1, 31, "Approved PASSPORT for Person 14");

        Sent sent = sent(1).get(0);
        assertEquals("audit-events", sent.topic());
        assertEquals("KycDocument:31", sent.key());
        assertEquals("AUDIT", sent.event().get("type").asString());
        assertEquals(Set.of("action", "entityType", "entityId", "actorId", "details"), fieldNames(sent.payload()));
        assertEquals("KYC_APPROVED", sent.payload().get("action").asString());
        assertEquals("KycDocument", sent.payload().get("entityType").asString());
        assertEquals(31, sent.payload().get("entityId").asLong());
        assertEquals(1, sent.payload().get("actorId").asLong());
        assertEquals("Approved PASSPORT for Person 14", sent.payload().get("details").asString());
    }

    @Test
    void everyAuditActionUsesTheEntityTypeFromTheContract() {
        Map<AuditAction, String> expected = Map.of(
                AuditAction.KYC_SUBMITTED, "KycDocument",
                AuditAction.KYC_RESUBMITTED, "KycDocument",
                AuditAction.KYC_APPROVED, "KycDocument",
                AuditAction.KYC_REJECTED, "KycDocument",
                AuditAction.ADVISOR_APPLICATION_SUBMITTED, "AdvisorApplication",
                AuditAction.ADVISOR_APPLICATION_APPROVED, "AdvisorApplication",
                AuditAction.ADVISOR_APPLICATION_REJECTED, "AdvisorApplication",
                AuditAction.CERTIFICATE_REPLACED, "ApplicationCertificate");
        assertEquals(expected.keySet(), Set.of(AuditAction.values()));

        for (AuditAction action : AuditAction.values()) {
            audit.record(action, 1, 9, "x");
        }

        List<Sent> sent = sent(AuditAction.values().length);
        for (int i = 0; i < sent.size(); i++) {
            AuditAction action = AuditAction.values()[i];
            assertEquals(expected.get(action) + ":9", sent.get(i).key(), action.name());
            assertEquals(expected.get(action), sent.get(i).payload().get("entityType").asString(), action.name());
        }
    }

    // failures never reach the caller

    @Test
    void aSendThatFailsLaterIsOnlyLogged() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("delivery timed out")));

        assertDoesNotThrow(() -> notifications.publish(14, NotificationType.KYC_REVIEWED, "x"));
        assertDoesNotThrow(() -> checkEvents.push(14, Map.of("type", "KYC_CHECKED", "documentIds", List.of(1L))));
        assertDoesNotThrow(() -> audit.record(AuditAction.KYC_SUBMITTED, 14, 1, "x"));
    }

    @Test
    void aSendThatThrowsStraightAwayIsOnlyLogged() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenThrow(new KafkaException("broker not available"));

        assertDoesNotThrow(() -> notifications.publish(14, NotificationType.KYC_REVIEWED, "x"));
        assertDoesNotThrow(() -> checkEvents.push(14, Map.of("type", "KYC_CHECKED", "documentIds", List.of(1L))));
        assertDoesNotThrow(() -> audit.record(AuditAction.KYC_SUBMITTED, 14, 1, "x"));
    }
}
