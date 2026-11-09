package com.example.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tracks open Server-Sent Events connections per user and pushes messages to them in real time.
 * A message is any map, not only a notification: the frontend also gets small messages such as
 * KYC_CHECKED and CERTIFICATES_CHECKED.
 */
@Slf4j
@Service
public class NotificationStreamService {
    private static final long TIMEOUT_MS = 25 * 60 * 1000L;   // 25 minutes

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    // the tests replace this to get an emitter they can look inside
    SseEmitter newEmitter() {
        return new SseEmitter(TIMEOUT_MS);
    }

    /** Opens an SSE connection for a user and auto-removes it on completion, timeout or error. */
    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = newEmitter();
        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        log.debug("SSE subscriber connected for user {}", userId);

        emitter.onCompletion(() -> {
            remove(userId, emitter);
            log.debug("SSE subscriber disconnected for user {}", userId);
        });
        emitter.onTimeout(() -> {
            remove(userId, emitter);
            log.debug("SSE subscriber timed out for user {}", userId);
        });
        emitter.onError(e -> {
            remove(userId, emitter);
            log.debug("SSE subscriber error for user {}", userId);
        });

        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            remove(userId, emitter);
        }
        return emitter;
    }

    /** Pushes a message to every open connection for a user, dropping any that fail. */
    public void push(Long userId, Map<String, Object> message) {
        for (SseEmitter emitter : emitters.getOrDefault(userId, List.of())) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(message));
            } catch (IOException e) {
                remove(userId, emitter);
                log.warn("Failed to push a message to user {}", userId);
            }
        }
    }

    private void remove(Long userId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(userId);
        if (list != null) {
            list.remove(emitter);
        }
    }
}
