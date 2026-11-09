package com.example.notificationservice.mapper;

import com.example.notificationservice.model.Notification;
import com.example.notificationservice.model.response.NotificationResponse;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    public NotificationResponse toResponse(Notification notif) {
        return new NotificationResponse(
                notif.getId(), notif.getType(), notif.getMessage(), notif.isRead(),
                notif.getRelatedAppointmentId(), notif.getCreatedAt());
    }
}
