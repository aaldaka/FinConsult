package com.example.finConsult.service;

import com.example.finConsult.mapper.ResponseMapper;
import com.example.finConsult.model.AuditLog;
import com.example.finConsult.model.User;
import com.example.finConsult.model.enums.AuditAction;
import com.example.finConsult.model.response.AuditLogResponse;
import com.example.finConsult.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Records and retrieves audit log entries for sensitive actions across the app
 * (account changes, appointment lifecycle events, KYC reviews, etc.).
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final int MAX_DETAILS_LENGTH = 800;
    private final AuditLogRepository auditLogRepository;
    private final ResponseMapper mapper;

    public Page<AuditLogResponse> getLogs(Pageable p, AuditAction action){ // by default its 20 entries per page. 1st page no prev link
        Page<AuditLog> logs = (action == null)
                ? auditLogRepository.findAll(p)
                : auditLogRepository.findByAction(action.name(), p);
        return logs.map(mapper::toAuditLog);
    }

    /** Persists one entry; details are truncated and must never include passwords or tokens. */
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