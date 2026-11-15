package com.example.notificationservice.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// runs on finconsult_test (TEST_DB_URL) without kafka and without a mail server: the handler is called with the
// message text and JavaMailSender is a mock. Every link carries a fresh token, and every test checks that the token
// shows up in no log line. The processed_events rows the test made are deleted afterwards
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
@ExtendWith(OutputCaptureExtension.class)
class EmailEventHandlerTest {

    private static final String TO = "client-" + UUID.randomUUID() + "@example.com";

    @Autowired EmailEventHandler handler;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean JavaMailSender mailSender;

    private final String token = UUID.randomUUID().toString();
    private final List<UUID> eventIds = new ArrayList<>();

    @AfterEach
    void deleteWhatThisTestCreated() {
        for (UUID id : eventIds) {
            jdbcTemplate.update("DELETE FROM processed_events WHERE event_id = ?", id);
        }
    }

    // helpers

    private UUID newEventId() {
        UUID id = UUID.randomUUID();
        eventIds.add(id);
        return id;
    }

    private String event(UUID eventId, String type, String payload) {
        return "{\"eventId\": \"" + eventId + "\", \"type\": \"" + type + "\", \"occurredAt\": \"2026-11-15T10:32:05Z\", "
                + "\"source\": \"user-service\", \"payload\": " + payload + "}";
    }

    private String verifyLink() {
        return "http://localhost:5173/verify-email?token=" + token;
    }

    private String resetLink() {
        return "http://localhost:5173/reset-password?token=" + token;
    }

    private String verification(UUID eventId) {
        return event(eventId, "EMAIL", "{\"template\": \"EMAIL_VERIFICATION\", \"to\": \"" + TO + "\", "
                + "\"data\": {\"link\": \"" + verifyLink() + "\"}}");
    }

    private String reset(UUID eventId) {
        return event(eventId, "EMAIL", "{\"template\": \"PASSWORD_RESET\", \"to\": \"" + TO + "\", "
                + "\"data\": {\"link\": \"" + resetLink() + "\", \"expiresInMinutes\": 5}}");
    }

    private SimpleMailMessage theOnlyEmailSent() {
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        return sent.getValue();
    }

    private boolean processed(UUID eventId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM processed_events WHERE event_id = ?", Long.class, eventId) == 1;
    }

    private void assertNoSecretLogged(CapturedOutput output) {
        assertFalse(output.getAll().contains(token), "a log line contains the token");
        assertFalse(output.getAll().contains("token="), "a log line contains a link");
    }

    // the templates

    @Test
    void theVerificationEmailGoesToTheRecipientWithItsLink(CapturedOutput output) {
        UUID eventId = newEventId();

        handler.handle(verification(eventId));

        SimpleMailMessage email = theOnlyEmailSent();
        assertArrayEquals(new String[] {TO}, email.getTo());
        assertEquals("test-sender@example.com", email.getFrom());
        assertEquals("Verify your FinConsult email", email.getSubject());
        assertEquals("Click to verify your email: " + verifyLink() + "\n\nThis link expires in 24 hours.", email.getText());
        assertTrue(processed(eventId));
        assertNoSecretLogged(output);
    }

    @Test
    void thePasswordResetEmailGoesToTheRecipientWithItsLinkAndExpiry(CapturedOutput output) {
        handler.handle(reset(newEventId()));

        SimpleMailMessage email = theOnlyEmailSent();
        assertArrayEquals(new String[] {TO}, email.getTo());
        assertEquals("Reset your FinConsult password", email.getSubject());
        assertEquals("Click to reset your password: " + resetLink()
                + "\n\nThis link expires in 5 minutes. If you didn't request this, ignore this email.", email.getText());
        assertNoSecretLogged(output);
    }

    // idempotency and failures

    @Test
    void aDuplicateEventIdSendsOnce(CapturedOutput output) {
        UUID eventId = newEventId();

        handler.handle(verification(eventId));
        handler.handle(verification(eventId));

        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
        assertNoSecretLogged(output);
    }

    @Test
    void aMailFailureThrowsAndLeavesNoProcessedRowSoARetrySends(CapturedOutput output) {
        UUID eventId = newEventId();
        doThrow(new MailSendException("smtp unavailable")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThrows(MailSendException.class, () -> handler.handle(reset(eventId)));

        assertFalse(processed(eventId), "the failed email is not marked as processed");
        assertNoSecretLogged(output);
    }

    @Test
    void anUnknownTemplateIsSkipped(CapturedOutput output) {
        UUID eventId = newEventId();

        handler.handle(event(eventId, "EMAIL", "{\"template\": \"WELCOME\", \"to\": \"" + TO + "\", \"data\": {\"link\": \"" + verifyLink() + "\"}}"));

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        assertFalse(processed(eventId));
        assertNoSecretLogged(output);
    }

    @Test
    void aMalformedEventThrowsTheNotRetryableExceptionWithoutTheLink(CapturedOutput output) {
        List<String> malformed = List.of(
                "{\"payload\": {\"data\": {\"link\": \"" + verifyLink() + "\"", // cut off json
                event(newEventId(), "EMAIL", "null"),
                event(newEventId(), "EMAIL", "{\"template\": \"EMAIL_VERIFICATION\", \"data\": {\"link\": \"" + verifyLink() + "\"}}"), // no to
                event(newEventId(), "EMAIL", "{\"template\": \"EMAIL_VERIFICATION\", \"to\": \"" + TO + "\", \"data\": {}}"), // no link
                event(newEventId(), "EMAIL", "{\"template\": \"PASSWORD_RESET\", \"to\": \"" + TO + "\", \"data\": {\"link\": \"" + resetLink() + "\"}}"),
                event(newEventId(), "EMAIL", "{\"template\": \"PASSWORD_RESET\", \"to\": \"" + TO + "\", \"data\": \"" + resetLink() + "\"}"));

        for (String message : malformed) {
            InvalidEventException e = assertThrows(InvalidEventException.class, () -> handler.handle(message));
            assertFalse(e.getMessage().contains(token), "the exception message contains the token");
            assertEquals(null, e.getCause(), "the cause could quote the message");
        }

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
        assertNoSecretLogged(output);
    }
}
