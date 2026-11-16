package com.example.userservice.integration;

import com.example.userservice.config.KafkaProducerConfig;
import com.example.userservice.model.enums.AuditAction;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// no broker: the KafkaTemplate is a mock that records what was sent, and the json is read back to check it against
// docs/events.md. The last test uses a real KafkaTemplate over kafka's MockProducer, to see what a failed send logs
@ExtendWith(OutputCaptureExtension.class)
class EventPublisherTest {

    private static final String TO = "client@example.com";

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final String token = UUID.randomUUID().toString();

    private KafkaAuditRecorder audit;
    private KafkaEmailPublisher emails;

    @BeforeEach
    void setUp() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        EventPublisher events = new EventPublisher(kafkaTemplate, objectMapper);
        audit = new KafkaAuditRecorder(events);
        emails = new KafkaEmailPublisher(events);
    }

    @AfterEach
    void endTheTransactionOfATest() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
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

    private String resetLink() {
        return "http://localhost:5173/reset-password?token=" + token;
    }

    // the envelope

    @Test
    void theEnvelopeHasTheFiveFieldsOfTheContract() {
        audit.record(1L, AuditAction.USER_DEACTIVATED, "User", 14L, "Account deactivated");

        JsonNode event = sent(1).get(0).event();
        assertEquals(Set.of("eventId", "type", "occurredAt", "source", "payload"), fieldNames(event));
        assertDoesNotThrow(() -> UUID.fromString(event.get("eventId").asString()));
        assertTrue(event.get("occurredAt").isString(), "occurredAt is an ISO-8601 string, not a number");
        assertDoesNotThrow(() -> OffsetDateTime.parse(event.get("occurredAt").asString()));
        assertEquals("user-service", event.get("source").asString());
    }

    // AUDIT, one per action with the entity type its caller passes

    @Test
    void everyAuditActionGoesToAuditEventsKeyedByEntityTypeAndId() {
        record Case(AuditAction action, String entityType) {}
        List<Case> cases = List.of(
                new Case(AuditAction.PROFILE_UPDATED, "Profile"),
                new Case(AuditAction.PROFILE_PICTURE_UPDATED, "Profile"),
                new Case(AuditAction.USER_DEACTIVATED, "User"),
                new Case(AuditAction.ADVISOR_DEACTIVATED, "Advisor"),
                new Case(AuditAction.ADVISOR_CREATED, "Advisor"));
        assertEquals(AuditAction.values().length, cases.size());

        for (Case c : cases) {
            audit.record(1L, c.action(), c.entityType(), 42L, "details of " + c.action());
        }

        List<Sent> sent = sent(cases.size());
        for (int i = 0; i < cases.size(); i++) {
            Case c = cases.get(i);
            Sent s = sent.get(i);
            assertEquals("audit-events", s.topic());
            assertEquals(c.entityType() + ":42", s.key());
            assertEquals("AUDIT", s.event().get("type").asString());
            assertEquals(Set.of("action", "entityType", "entityId", "actorId", "details"), fieldNames(s.payload()));
            assertEquals(c.action().name(), s.payload().get("action").asString());
            assertEquals(c.entityType(), s.payload().get("entityType").asString());
            assertEquals(42, s.payload().get("entityId").asLong());
            assertEquals(1, s.payload().get("actorId").asLong());
            assertEquals("details of " + c.action(), s.payload().get("details").asString());
        }
    }

    // EMAIL

    @Test
    void theVerificationEmailGoesToEmailEventsKeyedByTheAddress() {
        emails.verification(TO, "http://localhost:5173/verify-email?token=" + token);

        Sent sent = sent(1).get(0);
        assertEquals("email-events", sent.topic());
        assertEquals(TO, sent.key());
        assertEquals("EMAIL", sent.event().get("type").asString());
        assertEquals(Set.of("template", "to", "data"), fieldNames(sent.payload()));
        assertEquals("EMAIL_VERIFICATION", sent.payload().get("template").asString());
        assertEquals(TO, sent.payload().get("to").asString());
        assertEquals(Set.of("link"), fieldNames(sent.payload().get("data")));
        assertEquals("http://localhost:5173/verify-email?token=" + token, sent.payload().get("data").get("link").asString());
    }

    @Test
    void thePasswordResetEmailCarriesTheLinkAndHowLongItIsValid() {
        emails.passwordReset(TO, resetLink(), 5);

        Sent sent = sent(1).get(0);
        assertEquals("email-events", sent.topic());
        assertEquals(TO, sent.key());
        assertEquals("PASSWORD_RESET", sent.payload().get("template").asString());
        assertEquals(TO, sent.payload().get("to").asString());
        assertEquals(Set.of("link", "expiresInMinutes"), fieldNames(sent.payload().get("data")));
        assertEquals(resetLink(), sent.payload().get("data").get("link").asString());
        assertEquals(5, sent.payload().get("data").get("expiresInMinutes").asInt());
    }

    // after the commit

    @Test
    void insideATransactionTheEventWaitsForTheCommit() {
        TransactionSynchronizationManager.initSynchronization();

        emails.passwordReset(TO, resetLink(), 5);
        verifyNoInteractions(kafkaTemplate);

        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        assertEquals("PASSWORD_RESET", sent(1).get(0).payload().get("template").asString());
    }

    @Test
    void aRolledBackTransactionSendsNothing() {
        TransactionSynchronizationManager.initSynchronization();

        audit.record(1L, AuditAction.USER_DEACTIVATED, "User", 14L, "Account deactivated");
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        verifyNoInteractions(kafkaTemplate);
    }

    // failures never reach the caller and never log the link

    @Test
    void aSendThatFailsIsOnlyLoggedWithoutTheLink(CapturedOutput output) {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("delivery timed out")))
                .thenThrow(new KafkaException("broker not available"));

        assertDoesNotThrow(() -> emails.passwordReset(TO, resetLink(), 5));
        assertDoesNotThrow(() -> emails.verification(TO, resetLink()));

        assertTrue(output.getAll().contains("Could not send event"));
        assertFalse(output.getAll().contains(token), "a log line contains the token");
        assertFalse(output.getAll().contains("token="), "a log line contains a link");
    }

    @Test
    void theRealTemplateLogsAFailedSendWithoutTheKeyOrTheValue(CapturedOutput output) {
        MockProducer<String, String> producer = new MockProducer<>(false, null, new StringSerializer(), new StringSerializer());
        KafkaTemplate<String, String> template = new KafkaProducerConfig().kafkaTemplate(() -> producer);
        KafkaEmailPublisher realEmails = new KafkaEmailPublisher(new EventPublisher(template, objectMapper));

        assertDoesNotThrow(() -> realEmails.passwordReset(TO, resetLink(), 5));
        producer.errorNext(new RuntimeException("broker not available"));

        assertTrue(output.getAll().contains("Could not send event"));
        assertFalse(output.getAll().contains(token), "a log line contains the token");
        assertFalse(output.getAll().contains("token="), "a log line contains a link");
        assertFalse(output.getAll().contains(TO), "a log line contains the address");
    }
}
