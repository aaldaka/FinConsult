package com.example.documentservice.integration;

import com.example.documentservice.model.enums.NotificationType;

// sends the same notification to every admin, for example when files are flagged
public interface AdminNotifier {

    void notifyAdmins(NotificationType type, String message);
}
