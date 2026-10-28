package com.example.finConsult.event;

import com.example.finConsult.service.NotificationStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Pushes new notifications to the user's open SSE connections, only after the transaction commits. */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationPushListener {
    private final NotificationStreamService streamService;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreated(NotificationCreatedEvent event) {
        log.info("Pushing notification to user {} on {}", event.userId(), Thread.currentThread().getName());
        streamService.push(event.userId(), event.payload());
    }
}