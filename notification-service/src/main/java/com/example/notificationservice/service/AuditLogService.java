package com.example.notificationservice.service;

import com.example.notificationservice.client.UserClient;
import com.example.notificationservice.client.UserSummary;
import com.example.notificationservice.model.AuditLog;
import com.example.notificationservice.model.enums.AuditAction;
import com.example.notificationservice.model.response.AuditLogResponse;
import com.example.notificationservice.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

// the audit log for the admins, newest first, optionally only one action. The actors' email and name come from the
// user service in one call for the page; it runs after the read, outside any transaction
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserClient userClient;

    public Page<AuditLogResponse> getLogs(Pageable pageable, AuditAction action) {
        Page<AuditLog> logs = action == null
                ? auditLogRepository.findAll(pageable)
                : auditLogRepository.findByAction(action.name(), pageable);

        Set<Long> actorIds = logs.stream().map(AuditLog::getActorId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, UserSummary> actors = userClient.lookup(actorIds);
        return logs.map(entry -> toResponse(entry, actors));
    }

    private AuditLogResponse toResponse(AuditLog entry, Map<Long, UserSummary> actors) {
        String email = "SYSTEM";
        String name = "System";
        if (entry.getActorId() != null) {
            UserSummary actor = actors.get(entry.getActorId());
            email = actor != null && actor.email() != null ? actor.email() : "Unknown";
            name = actor != null && actor.displayName() != null ? actor.displayName() : "Unknown";
        }
        return new AuditLogResponse(entry.getId(), email, name, entry.getAction(), entry.getEntityType(),
                entry.getEntityId(), entry.getDetails(), entry.getTimestamp());
    }
}
