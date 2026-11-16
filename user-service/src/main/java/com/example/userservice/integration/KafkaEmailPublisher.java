package com.example.userservice.integration;

import org.springframework.stereotype.Component;

import java.util.Map;

// an EMAIL event on email-events, keyed by the recipient's address, see docs/events.md
@Component
public class KafkaEmailPublisher implements EmailPublisher {

    static final String TOPIC = "email-events";
    static final String TYPE = "EMAIL";
    static final String EMAIL_VERIFICATION = "EMAIL_VERIFICATION";
    static final String PASSWORD_RESET = "PASSWORD_RESET";

    private final EventPublisher events;

    public KafkaEmailPublisher(EventPublisher events) {
        this.events = events;
    }

    record EmailPayload(String template, String to, Map<String, Object> data) {}

    @Override
    public void verification(String to, String link) {
        events.publish(TOPIC, to, TYPE, new EmailPayload(EMAIL_VERIFICATION, to, Map.of("link", link)));
    }

    @Override
    public void passwordReset(String to, String link, int expiresInMinutes) {
        events.publish(TOPIC, to, TYPE, new EmailPayload(PASSWORD_RESET, to, Map.of("link", link, "expiresInMinutes", expiresInMinutes)));
    }
}
