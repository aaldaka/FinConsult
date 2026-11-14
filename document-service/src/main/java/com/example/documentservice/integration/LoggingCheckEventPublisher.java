package com.example.documentservice.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

// PLACEHOLDER: nothing is pushed yet, the event is only logged
@Slf4j
@Component
public class LoggingCheckEventPublisher implements CheckEventPublisher {

    @Override
    public void push(long userId, Map<String, Object> event) {
        log.info("PLACEHOLDER event to user {}: {}", userId, event);
    }
}
