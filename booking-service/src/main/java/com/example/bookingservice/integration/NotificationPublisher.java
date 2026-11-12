package com.example.bookingservice.integration;

import com.example.bookingservice.model.enums.NotificationType;

public interface NotificationPublisher {

    void publish(long recipientUserId, NotificationType type, String message, long appointmentId);
}
