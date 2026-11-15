package com.example.notificationservice.event;

import com.example.notificationservice.config.KafkaConsumerConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// listens to email-events. It does not catch anything: a failure has to reach the error handler in
// KafkaConsumerConfig, which retries it and then sends it to email-events.DLT
@Component
@RequiredArgsConstructor
public class EmailEventConsumer {

    static final String TOPIC = "email-events";

    private final EmailEventHandler handler;

    @KafkaListener(topics = TOPIC, groupId = KafkaConsumerConfig.GROUP_ID)
    public void onEvent(String message) {
        handler.handle(message);
    }
}
