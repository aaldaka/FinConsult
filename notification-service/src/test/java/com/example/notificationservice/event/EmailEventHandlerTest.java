package com.example.notificationservice.event;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
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
import static org.mockito.Mockito.when;

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

    @BeforeEach
    void aMailSenderThatBuildsRealMessages() {
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));
    }

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

    // saved first, as sending would, so every part gets its content type header
    private MimeMessage theOnlyEmailSent() throws Exception {
        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        sent.getValue().saveChanges();
        return sent.getValue();
    }

    // the decoded body of the first part with this content type, looking through the nested multiparts
    private static String part(Part part, String contentType) throws Exception {
        Object content = part.getContent();
        if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                String found = part(multipart.getBodyPart(i), contentType);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        return part.isMimeType(contentType) ? (String) content : null;
    }

    private void assertSentTo(MimeMessage email, String subject) throws Exception {
        assertArrayEquals(new InternetAddress[] {new InternetAddress(TO)}, email.getAllRecipients());
        InternetAddress from = (InternetAddress) email.getFrom()[0];
        assertEquals("test-sender@example.com", from.getAddress());
        assertEquals("FinConsult", from.getPersonal());
        assertEquals(subject, email.getSubject());
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
    void theVerificationEmailGoesToTheRecipientWithItsLink(CapturedOutput output) throws Exception {
        UUID eventId = newEventId();

        handler.handle(verification(eventId));

        MimeMessage email = theOnlyEmailSent();
        assertSentTo(email, "Verify your FinConsult email");
        assertEquals("Click to verify your email: " + verifyLink() + "\n\nThis link expires in 24 hours.", part(email, "text/plain"));
        String html = part(email, "text/html");
        assertTrue(html.contains("Confirm your email"));
        assertTrue(html.contains("href=\"" + verifyLink() + "\""), "the button links to the verification link");
        assertTrue(html.contains("This link expires in 24 hours."));
        assertTrue(processed(eventId));
        assertNoSecretLogged(output);
    }

    @Test
    void thePasswordResetEmailGoesToTheRecipientWithItsLinkAndExpiry(CapturedOutput output) throws Exception {
        handler.handle(reset(newEventId()));

        MimeMessage email = theOnlyEmailSent();
        assertSentTo(email, "Reset your FinConsult password");
        assertEquals("Click to reset your password: " + resetLink()
                + "\n\nThis link expires in 5 minutes. If you didn't request this, ignore this email.", part(email, "text/plain"));
        String html = part(email, "text/html");
        assertTrue(html.contains("Reset your password"));
        assertTrue(html.contains("href=\"" + resetLink() + "\""), "the button links to the reset link");
        assertTrue(html.contains("This link expires in 5 minutes."));
        assertNoSecretLogged(output);
    }

    // idempotency and failures

    @Test
    void aDuplicateEventIdSendsOnce(CapturedOutput output) {
        UUID eventId = newEventId();

        handler.handle(verification(eventId));
        handler.handle(verification(eventId));

        verify(mailSender, times(1)).send(any(MimeMessage.class));
        assertNoSecretLogged(output);
    }

    @Test
    void aMailFailureThrowsAndLeavesNoProcessedRowSoARetrySends(CapturedOutput output) {
        UUID eventId = newEventId();
        doThrow(new MailSendException("smtp unavailable")).when(mailSender).send(any(MimeMessage.class));

        assertThrows(MailSendException.class, () -> handler.handle(reset(eventId)));

        assertFalse(processed(eventId), "the failed email is not marked as processed");
        assertNoSecretLogged(output);
    }

    @Test
    void anUnknownTemplateIsSkipped(CapturedOutput output) {
        UUID eventId = newEventId();

        handler.handle(event(eventId, "EMAIL", "{\"template\": \"WELCOME\", \"to\": \"" + TO + "\", \"data\": {\"link\": \"" + verifyLink() + "\"}}"));

        verify(mailSender, never()).send(any(MimeMessage.class));
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

        verify(mailSender, never()).send(any(MimeMessage.class));
        assertNoSecretLogged(output);
    }
}
