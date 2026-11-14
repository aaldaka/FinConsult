package com.example.documentservice.integration;

import com.example.documentservice.client.AdminInfo;
import com.example.documentservice.client.UserClient;
import com.example.documentservice.model.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

// asks the user service who the admins are and sends each of them the notification. It runs on the doc-worker pool
// after a check, so a failed lookup is only logged: it must never break the check that called it
@Slf4j
@Component
@RequiredArgsConstructor
public class UserServiceAdminNotifier implements AdminNotifier {

    private final UserClient userClient;
    private final NotificationPublisher notificationPublisher;

    @Override
    public void notifyAdmins(NotificationType type, String message) {
        List<AdminInfo> admins;
        try {
            admins = userClient.admins();
        } catch (RuntimeException e) {
            log.warn("Could not look up the admins to notify ({}): {}", message, e.getMessage());
            return;
        }
        for (AdminInfo admin : admins) {
            try {
                notificationPublisher.publish(admin.id(), type, message);
            } catch (RuntimeException e) {
                log.warn("Could not notify admin {}: {}", admin.id(), e.getMessage());
            }
        }
    }
}
