package com.example.documentservice.integration;

import com.example.documentservice.model.enums.NotificationType;
import org.springframework.stereotype.Component;

// a NOTIFICATION event on notification-events, keyed by the recipient so one user's notifications stay in order.
// Document notifications are not about an appointment, so relatedAppointmentId is always null
@Component
public class KafkaNotificationPublisher implements NotificationPublisher {

    static final String TOPIC = "notification-events";
    static final String TYPE = "NOTIFICATION";

    private final EventPublisher events;

    public KafkaNotificationPublisher(EventPublisher events) {
        this.events = events;
    }

    record NotificationPayload(long userId, String notificationType, String message, Long relatedAppointmentId) {}

    @Override
    public void publish(long recipientUserId, NotificationType type, String message) {
        events.publish(TOPIC, String.valueOf(recipientUserId), TYPE,
                new NotificationPayload(recipientUserId, type.name(), message, null));
    }
}
