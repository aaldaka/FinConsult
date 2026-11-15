package com.example.notificationservice.event;

import com.example.notificationservice.service.NotificationStreamService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// runs on finconsult_test (TEST_DB_URL) without kafka: the handler is called directly with the message text. The user
// is inserted by the test and deleted afterwards with its notifications and the event ids the test used. The stream is
// a mock, so the test can see what was pushed and whether a transaction was still open at that moment
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class NotificationEventHandlerTest {

    @Autowired NotificationEventHandler handler;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean NotificationStreamService streamService;

    private long userId;
    private final List<UUID> eventIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        userId = jdbcTemplate.queryForObject(
                "INSERT INTO users (email, password, role, user_status, blacklisted, no_show_strikes, created_at, updated_at) "
                        + "VALUES (?, 'not-a-real-hash', 'CLIENT', 'ACTIVE', false, 0, now(), now()) RETURNING id",
                Long.class, "notification-events-" + UUID.randomUUID() + "@test.com");
    }

    @AfterEach
    void deleteWhatThisTestCreated() {
        jdbcTemplate.update("DELETE FROM notifications WHERE user_id = ?", userId);
        for (UUID id : eventIds) {
            jdbcTemplate.update("DELETE FROM processed_events WHERE event_id = ?", id);
        }
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
    }

    // helpers

    private UUID newEventId() {
        UUID id = UUID.randomUUID();
        eventIds.add(id);
        return id;
    }

    private String event(UUID eventId, String type, String payload) {
        return "{\"eventId\": \"" + eventId + "\", \"type\": \"" + type + "\", \"occurredAt\": \"2026-11-15T10:30:00Z\", "
                + "\"source\": \"booking-service\", \"payload\": " + payload + "}";
    }

    private String notification(UUID eventId, long recipient) {
        return event(eventId, "NOTIFICATION", "{\"userId\": " + recipient + ", \"notificationType\": \"APPOINTMENT_CONFIRMED\", "
                + "\"message\": \"Your appointment is confirmed\", \"relatedAppointmentId\": null, \"somethingNew\": 1}");
    }

    private long notificationsOf(long id) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ?", Long.class, id);
    }

    private boolean processed(UUID eventId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM processed_events WHERE event_id = ?", Long.class, eventId) == 1;
    }

    // NOTIFICATION

    @Test
    void aNotificationIsStoredAndPushedAfterTheCommit() {
        UUID eventId = newEventId();
        AtomicBoolean pushedInsideATransaction = new AtomicBoolean(true);
        AtomicLong rowsSeenByThePush = new AtomicLong(-1);
        doAnswer(invocation -> {
            pushedInsideATransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
            rowsSeenByThePush.set(notificationsOf(userId)); // read on a new connection: only committed rows are visible
            return null;
        }).when(streamService).push(anyLong(), any());

        handler.handle(notification(eventId, userId));

        assertEquals(1, notificationsOf(userId));
        assertEquals("APPOINTMENT_CONFIRMED", jdbcTemplate.queryForObject(
                "SELECT type FROM notifications WHERE user_id = ?", String.class, userId));
        assertTrue(processed(eventId));
        assertFalse(pushedInsideATransaction.get(), "the push ran while the transaction was still open");
        assertEquals(1, rowsSeenByThePush.get(), "the notification was already committed when it was pushed");
        verify(streamService).push(eq(userId), argThat(message -> "APPOINTMENT_CONFIRMED".equals(String.valueOf(message.get("type")))
                && "Your appointment is confirmed".equals(message.get("message")) && message.get("id") != null
                && Boolean.FALSE.equals(message.get("read")) && message.containsKey("createdAt")));
    }

    @Test
    void aDuplicateEventIdIsIgnored() {
        UUID eventId = newEventId();

        handler.handle(notification(eventId, userId));
        handler.handle(notification(eventId, userId));

        assertEquals(1, notificationsOf(userId), "the second delivery made no second notification");
        verify(streamService, times(1)).push(anyLong(), any());
    }

    @Test
    void aFailureInsideTheWorkLeavesNoProcessedRowSoARetryIsSafe() {
        UUID eventId = newEventId();
        long missingUser = 999_999_999L; // the foreign key to users makes the insert fail inside the transaction

        assertThrows(RuntimeException.class, () -> handler.handle(notification(eventId, missingUser)));

        assertFalse(processed(eventId), "the failed event is not marked as processed");
        verify(streamService, never()).push(anyLong(), any());
    }

    // STREAM_PUSH

    @Test
    void aStreamPushIsPushedWithItsDataAndNothingIsStored() {
        UUID eventId = newEventId();

        handler.handle(event(eventId, "STREAM_PUSH",
                "{\"userId\": " + userId + ", \"eventName\": \"KYC_CHECKED\", \"data\": {\"documentIds\": [31, 32]}}"));

        verify(streamService).push(eq(userId), eq(Map.of("type", "KYC_CHECKED", "documentIds", List.of(31, 32))));
        assertEquals(0, notificationsOf(userId), "a stream push stores no notification");
    }

    @Test
    void aStreamPushWithoutDataSendsOnlyTheEventName() {
        handler.handle(event(newEventId(), "STREAM_PUSH", "{\"userId\": " + userId + ", \"eventName\": \"CERTIFICATES_CHECKED\"}"));

        verify(streamService).push(eq(userId), eq(Map.of("type", "CERTIFICATES_CHECKED")));
    }

    // what is skipped and what is refused

    @Test
    void anUnknownTypeIsSkippedWithoutAnError() {
        UUID eventId = newEventId();

        handler.handle(event(eventId, "SOMETHING_NEW", "{\"userId\": " + userId + "}"));

        verify(streamService, never()).push(anyLong(), any());
        assertEquals(0, notificationsOf(userId));
        assertFalse(processed(eventId));
    }

    @Test
    void malformedJsonOrABrokenContractThrowsTheNotRetryableException() {
        assertThrows(InvalidEventException.class, () -> handler.handle("this is not json"));
        assertThrows(InvalidEventException.class, () -> handler.handle("{\"type\": \"NOTIFICATION\"}")); // no eventId
        assertThrows(InvalidEventException.class, () -> handler.handle(event(newEventId(), "NOTIFICATION", "null")));
        assertThrows(InvalidEventException.class, () -> handler.handle(event(newEventId(), "NOTIFICATION",
                "{\"userId\": " + userId + ", \"notificationType\": \"NOT_A_TYPE\", \"message\": \"x\"}")));
        assertThrows(InvalidEventException.class, () -> handler.handle(event(newEventId(), "STREAM_PUSH", "{\"userId\": " + userId + "}")));

        verify(streamService, never()).push(anyLong(), any());
        assertEquals(0, notificationsOf(userId));
    }
}
