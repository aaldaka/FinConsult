package com.example.notificationservice.event;

import com.example.notificationservice.model.AuditLog;
import com.example.notificationservice.repository.AuditLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.ZoneId;

// handles one message from audit-events, see docs/events.md. In one transaction the event id is recorded and the
// audit_logs row written; a duplicate id was already stored and is skipped. The row's timestamp is the event's
// occurredAt, so a late or replayed event still lands at the right time. Exceptions are not caught here: they reach
// the kafka error handler, which retries and then sends the message to audit-events.DLT. The details can contain
// names, so only the event id and the action are logged
@Slf4j
@Service
public class AuditEventHandler {

    static final String AUDIT = "AUDIT";
    static final int MAX_DETAILS_LENGTH = 800; // the audit_logs.details column

    private final ObjectMapper objectMapper;
    private final ProcessedEvents processedEvents;
    private final AuditLogRepository auditLogRepository;
    private final TransactionTemplate transactionTemplate;

    public AuditEventHandler(ObjectMapper objectMapper, ProcessedEvents processedEvents, AuditLogRepository auditLogRepository,
                             PlatformTransactionManager transactionManager) {
        this.objectMapper = objectMapper;
        this.processedEvents = processedEvents;
        this.auditLogRepository = auditLogRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void handle(String message) {
        EventEnvelope event = parse(message);
        if (!AUDIT.equals(event.type())) {
            log.warn("Skipping event {} with unknown type {} from {}", event.eventId(), event.type(), event.source());
            return;
        }

        AuditPayload payload = payload(event);
        if (payload.action() == null || payload.entityType() == null || payload.entityId() == null) {
            throw new InvalidEventException("Event " + event.eventId() + " needs action, entityType and entityId");
        }
        if (event.occurredAt() == null) {
            throw new InvalidEventException("Event " + event.eventId() + " needs occurredAt");
        }

        AuditLog entry = new AuditLog();
        entry.setActorId(payload.actorId());
        entry.setAction(payload.action());
        entry.setEntityType(payload.entityType());
        entry.setEntityId(payload.entityId());
        entry.setDetails(shortened(payload.details()));
        // the column has no time zone, so the time is stored in this server's zone like the original monolith did
        entry.setTimestamp(event.occurredAt().atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime());

        Boolean stored = transactionTemplate.execute(status -> {
            if (!processedEvents.markProcessed(event.eventId())) {
                return false;
            }
            auditLogRepository.save(entry);
            return true;
        });
        if (Boolean.TRUE.equals(stored)) {
            log.info("Audit {} stored (event {})", payload.action(), event.eventId());
        } else {
            log.info("Skipping event {}, it was already handled", event.eventId());
        }
    }

    private static String shortened(String details) {
        return details != null && details.length() > MAX_DETAILS_LENGTH ? details.substring(0, MAX_DETAILS_LENGTH) : details;
    }

    private EventEnvelope parse(String message) {
        EventEnvelope event;
        try {
            event = objectMapper.readValue(message, EventEnvelope.class);
        } catch (JacksonException e) {
            throw new InvalidEventException("The message is not a valid event"); // without the cause, it can quote the details
        }
        if (event == null || event.eventId() == null || event.type() == null) {
            throw new InvalidEventException("The event needs an eventId and a type");
        }
        return event;
    }

    private AuditPayload payload(EventEnvelope event) {
        if (event.payload() == null || event.payload().isNull()) {
            throw new InvalidEventException("Event " + event.eventId() + " has no payload");
        }
        try {
            return objectMapper.treeToValue(event.payload(), AuditPayload.class);
        } catch (JacksonException e) {
            throw new InvalidEventException("Event " + event.eventId() + " has a payload that does not fit AUDIT");
        }
    }
}
