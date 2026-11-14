package com.example.documentservice.integration;

import com.example.documentservice.model.enums.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// PLACEHOLDER: nothing is delivered yet, the notification is only logged
@Slf4j
@Component
public class LoggingNotificationPublisher implements NotificationPublisher {

    @Override
    public void publish(long recipientUserId, NotificationType type, String message) {
        log.info("PLACEHOLDER notification {} to user {}: {}", type, recipientUserId, message);
    }
}
