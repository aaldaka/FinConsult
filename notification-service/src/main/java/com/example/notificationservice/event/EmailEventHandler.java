package com.example.notificationservice.event;

import com.example.notificationservice.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

// handles one message from email-events, see docs/events.md. The event id is recorded and the email sent in one
// transaction: if sending fails the transaction rolls back, nothing is recorded and the kafka error handler retries
// the message and then sends it to the DLT. A duplicate id was already sent and is skipped.
// The messages carry one-time links, so nothing here logs the payload or puts it in an exception: only the event id
// and the template name. That is also why a parse error is thrown without its cause, which can quote the message
@Slf4j
@Service
public class EmailEventHandler {

    static final String EMAIL = "EMAIL";

    private final ObjectMapper objectMapper;
    private final ProcessedEvents processedEvents;
    private final EmailService emailService;
    private final TransactionTemplate transactionTemplate;

    public EmailEventHandler(ObjectMapper objectMapper, ProcessedEvents processedEvents, EmailService emailService,
                             PlatformTransactionManager transactionManager) {
        this.objectMapper = objectMapper;
        this.processedEvents = processedEvents;
        this.emailService = emailService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void handle(String message) {
        EventEnvelope event = parse(message);
        if (!EMAIL.equals(event.type())) {
            log.warn("Skipping event {} with unknown type {} from {}", event.eventId(), event.type(), event.source());
            return;
        }

        EmailPayload payload = payload(event);
        EmailTemplate template = EmailTemplate.find(payload.template());
        if (template == null) {
            log.warn("Skipping event {} with unknown email template {}", event.eventId(), payload.template());
            return;
        }
        if (payload.to() == null || payload.to().isBlank()) {
            throw new InvalidEventException("Event " + event.eventId() + " has no recipient");
        }
        String body = template.body(payload.data());

        Boolean sent = transactionTemplate.execute(status -> {
            if (!processedEvents.markProcessed(event.eventId())) {
                return false;
            }
            emailService.sendEmail(payload.to(), template.subject(), body);
            return true;
        });
        if (Boolean.TRUE.equals(sent)) {
            log.info("Email sent: template={} (event {})", template, event.eventId());
        } else {
            log.info("Skipping event {}, it was already handled", event.eventId());
        }
    }

    private EventEnvelope parse(String message) {
        EventEnvelope event;
        try {
            event = objectMapper.readValue(message, EventEnvelope.class);
        } catch (JacksonException e) {
            throw new InvalidEventException("The message is not a valid event");
        }
        if (event == null || event.eventId() == null || event.type() == null) {
            throw new InvalidEventException("The event needs an eventId and a type");
        }
        return event;
    }

    private EmailPayload payload(EventEnvelope event) {
        if (event.payload() == null || event.payload().isNull()) {
            throw new InvalidEventException("Event " + event.eventId() + " has no payload");
        }
        try {
            return objectMapper.treeToValue(event.payload(), EmailPayload.class);
        } catch (JacksonException e) {
            throw new InvalidEventException("Event " + event.eventId() + " has a payload that does not fit EMAIL");
        }
    }
}
