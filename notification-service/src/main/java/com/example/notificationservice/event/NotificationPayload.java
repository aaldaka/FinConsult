package com.example.notificationservice.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// payload of a NOTIFICATION event: a notification the user sees in their list
@JsonIgnoreProperties(ignoreUnknown = true)
public record NotificationPayload(Long userId, String notificationType, String message, Long relatedAppointmentId) {}
