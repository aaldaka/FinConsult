package com.example.notificationservice.model.response;

import java.time.LocalDateTime;

// the same fields the main app's GET /admin/logs returned. actorEmail is "SYSTEM" and actorName "System" when
// nobody did it
public record AuditLogResponse(Long id, String actorEmail, String actorName, String action, String entityType,
                               Long entityId, String details, LocalDateTime timestamp) {
}
