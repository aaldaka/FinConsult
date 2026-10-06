package com.example.finConsult.service;

import com.example.finConsult.model.AuditLog;
import com.example.finConsult.model.User;
import com.example.finConsult.model.enums.AuditAction;
import com.example.finConsult.model.response.AuditLogResponse;
import com.example.finConsult.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final int MAX_DETAILS_LENGTH = 800;
    private final AuditLogRepository auditLogRepository;

    public void log(User actor, AuditAction action, String entityType, Long entityId, String details) {
        AuditLog entry = new AuditLog();
        entry.setActor(actor); // who did what
        entry.setAction(action.name()); // action/transaction
        entry.setEntityType(entityType); //  what was done on
        entry.setEntityId(entityId);

        if (details != null && details.length() > MAX_DETAILS_LENGTH) {
            details = details.substring(0, MAX_DETAILS_LENGTH);
        }
        entry.setDetails(details);

        auditLogRepository.save(entry);
    }

}