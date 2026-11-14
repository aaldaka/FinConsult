package com.example.documentservice.integration;

import com.example.documentservice.model.enums.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// PLACEHOLDER: the admins are not looked up and nothing is delivered yet, the notification is only logged
@Slf4j
@Component
public class LoggingAdminNotifier implements AdminNotifier {

    @Override
    public void notifyAdmins(NotificationType type, String message) {
        log.info("PLACEHOLDER notification {} to every admin: {}", type, message);
    }
}
