package com.example.finConsult.event;

import com.example.finConsult.service.NotificationStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class NotificationPushListener {
    private final NotificationStreamService streamService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreated(NotificationCreatedEvent event) {
        streamService.push(event.userId(), event.payload());
    }
}