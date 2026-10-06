package com.example.finConsult.service;

import com.example.finConsult.model.AuditLog;
import com.example.finConsult.model.User;
import com.example.finConsult.model.enums.AuditAction;
import com.example.finConsult.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final int MAX_DETAILS_LENGTH = 800; // keep in sync with @Column(length) on AuditLog.details

    private final AuditLogRepository auditLogRepository;

    public void log(User actor, AuditAction action, String entityType, Long entityId, String details) {
        AuditLog log = new AuditLog();
        log.setActor(actor); // who did what
        log.setAction(action.name()); // action/transaction
        log.setEntityType(entityType); //  what was done on
        log.setEntityId(entityId);

        if (details != null && details.length() > MAX_DETAILS_LENGTH) {
            details = details.substring(0, MAX_DETAILS_LENGTH);
        }
        log.setDetails(details);

        auditLogRepository.save(log);
    }

}