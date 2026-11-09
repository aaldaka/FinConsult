package com.example.notificationservice.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationStreamServiceTest {

    // an emitter that records what was sent and lets the test fire the callbacks the service registered
    static class RecordingEmitter extends SseEmitter {
        final List<Object> sent = new ArrayList<>();
        int sendAttempts = 0;
        boolean failOnSend = false;
        Runnable completionCallback;
        Runnable timeoutCallback;
        Consumer<Throwable> errorCallback;

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            sendAttempts++;
            if (failOnSend) {
                throw new IOException("connection closed");
            }
            builder.build().forEach(part -> sent.add(part.getData()));
        }

        @Override public void onCompletion(Runnable callback) { this.completionCallback = callback; }
        @Override public void onTimeout(Runnable callback) { this.timeoutCallback = callback; }
        @Override public void onError(Consumer<Throwable> callback) { this.errorCallback = callback; }

        int received(Object message) {
            return (int) sent.stream().filter(message::equals).count();
        }
    }

    private final NotificationStreamService service = new NotificationStreamService() {
        @Override
        SseEmitter newEmitter() {
            return new RecordingEmitter();
        }
    };

    private RecordingEmitter subscribe(long userId) {
        return (RecordingEmitter) service.subscribe(userId);
    }

    private static final Map<String, Object> KYC_CHECKED = Map.of("type", "KYC_CHECKED", "documentIds", List.of(1L, 2L));
    private static final Map<String, Object> CERTIFICATES_CHECKED = Map.of("type", "CERTIFICATES_CHECKED", "applicationId", 9L);

    @Test
    void subscribingSendsAConnectedEvent() {
        RecordingEmitter emitter = subscribe(1L);

        assertTrue(emitter.sent.contains("ok"));
    }

    @Test
    void pushReachesEveryEmitterOfThatUserAndNobodyElses() {
        RecordingEmitter firstTab = subscribe(1L);
        RecordingEmitter secondTab = subscribe(1L);
        RecordingEmitter someoneElse = subscribe(2L);

        service.push(1L, KYC_CHECKED);

        assertEquals(1, firstTab.received(KYC_CHECKED));
        assertEquals(1, secondTab.received(KYC_CHECKED));
        assertEquals(0, someoneElse.received(KYC_CHECKED));
    }

    @Test
    void pushAcceptsAnyMapNotOnlyANotification() {
        RecordingEmitter emitter = subscribe(1L);
        Map<String, Object> notification = Map.of("id", 5L, "type", "KYC_REVIEWED", "message", "approved", "read", false);

        service.push(1L, KYC_CHECKED);
        service.push(1L, CERTIFICATES_CHECKED);
        service.push(1L, notification);

        assertEquals(1, emitter.received(KYC_CHECKED));
        assertEquals(1, emitter.received(CERTIFICATES_CHECKED));
        assertEquals(1, emitter.received(notification));
    }

    @Test
    void pushToAUserWithNoOpenStreamDoesNothing() {
        RecordingEmitter other = subscribe(2L);

        service.push(99L, KYC_CHECKED);

        assertEquals(0, other.received(KYC_CHECKED));
    }

    @Test
    void aFinishedEmitterIsRemoved() {
        RecordingEmitter finished = subscribe(1L);
        RecordingEmitter stillOpen = subscribe(1L);

        finished.completionCallback.run();
        service.push(1L, KYC_CHECKED);

        assertEquals(0, finished.received(KYC_CHECKED));
        assertEquals(1, stillOpen.received(KYC_CHECKED));
    }

    @Test
    void aTimedOutEmitterIsRemoved() {
        RecordingEmitter timedOut = subscribe(1L);
        RecordingEmitter stillOpen = subscribe(1L);

        timedOut.timeoutCallback.run();
        service.push(1L, KYC_CHECKED);

        assertEquals(0, timedOut.received(KYC_CHECKED));
        assertEquals(1, stillOpen.received(KYC_CHECKED));
    }

    @Test
    void anEmitterThatErroredIsRemoved() {
        RecordingEmitter broken = subscribe(1L);
        RecordingEmitter stillOpen = subscribe(1L);

        broken.errorCallback.accept(new IOException("broken pipe"));
        service.push(1L, KYC_CHECKED);

        assertEquals(0, broken.received(KYC_CHECKED));
        assertEquals(1, stillOpen.received(KYC_CHECKED));
    }

    @Test
    void anEmitterThatFailsWhilePushingIsDroppedAndTheOthersStillGetTheMessage() {
        RecordingEmitter failing = subscribe(1L);
        RecordingEmitter healthy = subscribe(1L);
        int attemptsBefore = failing.sendAttempts;
        failing.failOnSend = true;

        service.push(1L, KYC_CHECKED);
        service.push(1L, CERTIFICATES_CHECKED);

        assertEquals(attemptsBefore + 1, failing.sendAttempts); // tried once, then removed
        assertEquals(1, healthy.received(KYC_CHECKED));
        assertEquals(1, healthy.received(CERTIFICATES_CHECKED));
    }
}
