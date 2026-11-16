package com.example.notificationservice.event;

import com.example.notificationservice.config.KafkaConsumerConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// listens to audit-events. It does not catch anything: a failure has to reach the error handler in
// KafkaConsumerConfig, which retries it and then sends it to audit-events.DLT
@Component
@RequiredArgsConstructor
public class AuditEventConsumer {

    static final String TOPIC = "audit-events";

    private final AuditEventHandler handler;

    @KafkaListener(topics = TOPIC, groupId = KafkaConsumerConfig.GROUP_ID)
    public void onEvent(String message) {
        handler.handle(message);
    }
}
