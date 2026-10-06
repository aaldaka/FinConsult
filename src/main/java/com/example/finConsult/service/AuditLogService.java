package com.example.finConsult.service;

import com.example.finConsult.model.AuditLog;
import com.example.finConsult.model.User;
import com.example.finConsult.model.enums.AuditAction;
import com.example.finConsult.model.response.AuditLogResponse;
import com.example.finConsult.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final int MAX_DETAILS_LENGTH = 800;
    private final AuditLogRepository auditLogRepository;

    private AuditLogResponse toResponse(AuditLog entry) { // helper like profileservice, to map each field
        String actor = entry.getActor() != null ? entry.getActor().getEmail() : "SYSTEM";
        return new AuditLogResponse(entry.getId(), actor, entry.getAction(), entry.getEntityType(),
                entry.getEntityId(), entry.getDetails(), entry.getTimestamp());
    }

    public Page<AuditLogResponse> getLogs(Pageable p){ // by default its 20 entries per page. 1st page no prev link
        return auditLogRepository.findAll(p).map(this::toResponse);
    }

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