package com.example.notificationservice.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// runs on finconsult_test (TEST_DB_URL) without kafka: the handler is called with the message text. The actor is a
// user the test inserts; every audit row the test writes uses an entity id picked for this test, so the rows are
// found and deleted by it, with the processed_events rows of the event ids used and the user
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
@ExtendWith(OutputCaptureExtension.class)
class AuditEventHandlerTest {

    private static final String OCCURRED_AT = "2026-11-15T10:30:00Z";

    @Autowired AuditEventHandler handler;
    @Autowired JdbcTemplate jdbcTemplate;

    private long actorId;
    private long entityId;
    private final String secretDetails = "Approved PASSPORT for Person " + UUID.randomUUID();
    private final List<UUID> eventIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        actorId = jdbcTemplate.queryForObject(
                "INSERT INTO users (email, password, role, user_status, blacklisted, no_show_strikes, created_at, updated_at) "
                        + "VALUES (?, 'not-a-real-hash', 'ADMIN', 'ACTIVE', false, 0, now(), now()) RETURNING id",
                Long.class, "audit-events-" + UUID.randomUUID() + "@test.com");
        entityId = ThreadLocalRandom.current().nextLong(1_000_000_000L, 2_000_000_000L);
    }

    @AfterEach
    void deleteWhatThisTestCreated() {
        jdbcTemplate.update("DELETE FROM audit_logs WHERE entity_id = ?", entityId);
        for (UUID id : eventIds) {
            jdbcTemplate.update("DELETE FROM processed_events WHERE event_id = ?", id);
        }
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", actorId);
    }

    // helpers

    private UUID newEventId() {
        UUID id = UUID.randomUUID();
        eventIds.add(id);
        return id;
    }

    private String event(UUID eventId, String type, String payload) {
        return "{\"eventId\": \"" + eventId + "\", \"type\": \"" + type + "\", \"occurredAt\": \"" + OCCURRED_AT + "\", "
                + "\"source\": \"document-service\", \"payload\": " + payload + "}";
    }

    private String audit(UUID eventId, Long actor, String details) {
        return event(eventId, "AUDIT", "{\"action\": \"KYC_APPROVED\", \"entityType\": \"KycDocument\", \"entityId\": " + entityId
                + ", \"actorId\": " + actor + ", \"details\": \"" + details + "\", \"somethingNew\": true}");
    }

    private List<Map<String, Object>> rows() {
        return jdbcTemplate.queryForList("SELECT * FROM audit_logs WHERE entity_id = ?", entityId);
    }

    private boolean processed(UUID eventId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM processed_events WHERE event_id = ?", Long.class, eventId) == 1;
    }

    // storing

    @Test
    void anAuditEventIsStoredWithItsFieldsAndTheTimeItHappened(CapturedOutput output) {
        UUID eventId = newEventId();

        handler.handle(audit(eventId, actorId, secretDetails));

        List<Map<String, Object>> rows = rows();
        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        assertEquals("KYC_APPROVED", row.get("action"));
        assertEquals("KycDocument", row.get("entity_type"));
        assertEquals(entityId, ((Number) row.get("entity_id")).longValue());
        assertEquals(actorId, ((Number) row.get("actor_id")).longValue());
        assertEquals(secretDetails, row.get("details"));
        LocalDateTime expected = OffsetDateTime.parse(OCCURRED_AT).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        assertEquals(expected, ((java.sql.Timestamp) row.get("timestamp")).toLocalDateTime(), "the timestamp is occurredAt, not now");
        assertTrue(processed(eventId));
        assertFalse(output.getAll().contains(secretDetails), "a log line contains the details");
        assertTrue(output.getAll().contains("Audit KYC_APPROVED stored (event " + eventId + ")"));
    }

    @Test
    void somethingTheSystemDidHasNoActorAndLongDetailsAreCut() {
        String longDetails = "x".repeat(900);

        handler.handle(audit(newEventId(), null, longDetails));

        Map<String, Object> row = rows().get(0);
        assertNull(row.get("actor_id"));
        assertEquals(800, ((String) row.get("details")).length());
    }

    @Test
    void aDuplicateEventIdIsStoredOnce(CapturedOutput output) {
        UUID eventId = newEventId();

        handler.handle(audit(eventId, actorId, secretDetails));
        handler.handle(audit(eventId, actorId, secretDetails));

        assertEquals(1, rows().size());
        assertFalse(output.getAll().contains(secretDetails), "a log line contains the details");
    }

    @Test
    void aFailedInsertRollsBackAndLeavesNoProcessedRowSoARetryIsSafe(CapturedOutput output) {
        UUID eventId = newEventId();
        long missingActor = 999_999_999L; // the foreign key to users makes the insert fail inside the transaction

        assertThrows(RuntimeException.class, () -> handler.handle(audit(eventId, missingActor, secretDetails)));

        assertFalse(processed(eventId), "the failed event is not marked as processed");
        assertTrue(rows().isEmpty());
        assertFalse(output.getAll().contains(secretDetails), "a log line contains the details");
    }

    // skipped and refused

    @Test
    void anUnknownTypeIsSkippedWithoutAnError() {
        UUID eventId = newEventId();

        handler.handle(event(eventId, "SOMETHING_NEW", "{\"entityId\": " + entityId + "}"));

        assertTrue(rows().isEmpty());
        assertFalse(processed(eventId));
    }

    @Test
    void aMalformedEventThrowsTheNotRetryableExceptionSoItGoesStraightToTheDlt(CapturedOutput output) {
        List<String> malformed = List.of(
                "{\"payload\": {\"details\": \"" + secretDetails + "\"", // cut off json
                event(newEventId(), "AUDIT", "null"),
                event(newEventId(), "AUDIT", "{\"entityType\": \"KycDocument\", \"entityId\": " + entityId + "}"), // no action
                event(newEventId(), "AUDIT", "{\"action\": \"KYC_APPROVED\", \"entityType\": \"KycDocument\", \"entityId\": \"abc\"}"),
                "{\"eventId\": \"" + newEventId() + "\", \"type\": \"AUDIT\", \"payload\": {\"action\": \"KYC_APPROVED\", "
                        + "\"entityType\": \"KycDocument\", \"entityId\": " + entityId + "}}"); // no occurredAt

        for (String message : malformed) {
            InvalidEventException e = assertThrows(InvalidEventException.class, () -> handler.handle(message));
            assertNull(e.getCause(), "the cause could quote the details");
        }

        assertTrue(rows().isEmpty());
        assertFalse(output.getAll().contains(secretDetails), "a log line contains the details");
    }
}
