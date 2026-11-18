package com.example.notificationservice.event;

import com.example.notificationservice.mapper.NotificationMapper;
import com.example.notificationservice.model.Notification;
import com.example.notificationservice.model.enums.NotificationType;
import com.example.notificationservice.model.response.NotificationResponse;
import com.example.notificationservice.service.NotificationService;
import com.example.notificationservice.service.NotificationStreamService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

// handles one message from notification-events, see docs/events.md. In one transaction the event id is recorded and
// the work done; a duplicate id means the event was already handled and it is skipped. The push to the user's open
// stream happens only after that transaction commits. Exceptions are not caught here: they reach the kafka error
// handler, which retries and then sends the message to the dead letter topic
@Slf4j
@Service
public class NotificationEventHandler {

    static final String NOTIFICATION = "NOTIFICATION";
    static final String STREAM_PUSH = "STREAM_PUSH";

    private final ObjectMapper objectMapper;
    private final ProcessedEvents processedEvents;
    private final NotificationService notificationService;
    private final NotificationStreamService streamService;
    private final NotificationMapper mapper;
    private final TransactionTemplate transactionTemplate;

    public NotificationEventHandler(ObjectMapper objectMapper, ProcessedEvents processedEvents, NotificationService notificationService,
                                    NotificationStreamService streamService, NotificationMapper mapper,
                                    PlatformTransactionManager transactionManager) {
        this.objectMapper = objectMapper;
        this.processedEvents = processedEvents;
        this.notificationService = notificationService;
        this.streamService = streamService;
        this.mapper = mapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void handle(String message) {
        EventEnvelope event = parse(message);
        switch (event.type()) {
            case NOTIFICATION -> handleNotification(event);
            case STREAM_PUSH -> handleStreamPush(event);
            default -> log.warn("Skipping event {} with unknown type {} from {}", event.eventId(), event.type(), event.source());
        }
    }

    private void handleNotification(EventEnvelope event) {
        NotificationPayload payload = payload(event, NotificationPayload.class);
        if (payload.userId() == null || payload.message() == null || payload.notificationType() == null) {
            throw new InvalidEventException("Event " + event.eventId() + " needs userId, notificationType and message");
        }
        NotificationType type = notificationType(event, payload.notificationType());

        Notification saved = transactionTemplate.execute(status -> {
            if (!processedEvents.markProcessed(event.eventId())) {
                return null;
            }
            return notificationService.create(payload.userId(), type, payload.message(), payload.relatedAppointmentId());
        });
        if (saved == null) {
            log.info("Skipping event {}, it was already handled", event.eventId());
            return;
        }
        streamService.push(payload.userId(), toStreamMessage(mapper.toResponse(saved)));
        log.debug("Notification {} stored and pushed for user {} (event {})", saved.getId(), payload.userId(), event.eventId());
    }

    private void handleStreamPush(EventEnvelope event) {
        StreamPushPayload payload = payload(event, StreamPushPayload.class);
        if (payload.userId() == null || payload.eventName() == null) {
            throw new InvalidEventException("Event " + event.eventId() + " needs userId and eventName");
        }

        Boolean first = transactionTemplate.execute(status -> processedEvents.markProcessed(event.eventId()));
        if (!Boolean.TRUE.equals(first)) {
            log.info("Skipping event {}, it was already handled", event.eventId());
            return;
        }
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", payload.eventName());
        if (payload.data() != null) {
            message.putAll(payload.data());
        }
        streamService.push(payload.userId(), message);
        log.debug("Pushed {} to user {} (event {})", payload.eventName(), payload.userId(), event.eventId());
    }

    private EventEnvelope parse(String message) {
        EventEnvelope event;
        try {
            event = objectMapper.readValue(message, EventEnvelope.class);
        } catch (JacksonException e) {
            throw new InvalidEventException("The message is not a valid event", e);
        }
        if (event == null || event.eventId() == null || event.type() == null) {
            throw new InvalidEventException("The event needs an eventId and a type");
        }
        return event;
    }

    private <T> T payload(EventEnvelope event, Class<T> type) {
        if (event.payload() == null || event.payload().isNull()) {
            throw new InvalidEventException("Event " + event.eventId() + " has no payload");
        }
        try {
            return objectMapper.treeToValue(event.payload(), type);
        } catch (JacksonException e) {
            throw new InvalidEventException("Event " + event.eventId() + " has a payload that does not fit " + event.type(), e);
        }
    }

    private NotificationType notificationType(EventEnvelope event, String value) {
        try {
            return NotificationType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidEventException("Event " + event.eventId() + " has an unknown notificationType " + value, e);
        }
    }

    // the same json the original monolith pushed for a new notification: the NotificationResponse fields
    private Map<String, Object> toStreamMessage(NotificationResponse notification) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("id", notification.getId());
        message.put("type", notification.getType());
        message.put("message", notification.getMessage());
        message.put("read", notification.isRead());
        message.put("relatedAppointmentId", notification.getRelatedAppointmentId());
        message.put("createdAt", notification.getCreatedAt());
        return message;
    }
}
