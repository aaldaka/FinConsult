package com.example.documentservice.integration;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

// a STREAM_PUSH event on notification-events, keyed by the user. The callers pass the event as they always did,
// {"type": "KYC_CHECKED", "documentIds": [...]}: "type" becomes eventName and the other fields go into data
@Component
public class KafkaCheckEventPublisher implements CheckEventPublisher {

    static final String TOPIC = "notification-events";
    static final String TYPE = "STREAM_PUSH";

    private final EventPublisher events;

    public KafkaCheckEventPublisher(EventPublisher events) {
        this.events = events;
    }

    record StreamPushPayload(long userId, String eventName, Map<String, Object> data) {}

    @Override
    public void push(long userId, Map<String, Object> event) {
        Map<String, Object> data = new LinkedHashMap<>(event);
        Object eventName = data.remove("type");
        events.publish(TOPIC, String.valueOf(userId), TYPE,
                new StreamPushPayload(userId, String.valueOf(eventName), data.isEmpty() ? null : data));
    }
}
