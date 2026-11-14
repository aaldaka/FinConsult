package com.example.documentservice.integration;

import com.example.documentservice.model.enums.NotificationType;

public interface NotificationPublisher {

    void publish(long recipientUserId, NotificationType type, String message);
}
