package com.example.notificationservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamTicketServiceTest {

    // a clock the test can move forward
    static class MovableClock extends Clock {
        private volatile Instant now = Instant.parse("2026-01-01T10:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private MovableClock clock;
    private StreamTicketService service;

    @BeforeEach
    void setUp() {
        clock = new MovableClock();
        service = new StreamTicketService(clock);
    }

    @Test
    void ticketIsRandomUrlSafeAnd32Bytes() {
        String a = service.issue(1L);
        String b = service.issue(1L);

        assertNotEquals(a, b);
        assertEquals(32, Base64.getUrlDecoder().decode(a).length);
        assertTrue(a.matches("[A-Za-z0-9_-]+"));
    }

    @Test
    void ticketWorksOnce() {
        String ticket = service.issue(7L);

        assertEquals(Optional.of(7L), service.consume(ticket));
        assertEquals(Optional.empty(), service.consume(ticket));
    }

    @Test
    void ticketWorksUpToThirtySecondsAndNotAfter() {
        String fresh = service.issue(1L);
        String late = service.issue(2L);

        clock.advance(Duration.ofSeconds(30));
        assertEquals(Optional.of(1L), service.consume(fresh));

        clock.advance(Duration.ofSeconds(1));
        assertEquals(Optional.empty(), service.consume(late));
    }

    @Test
    void expiredTicketCannotBeTriedAgain() {
        String ticket = service.issue(1L);
        clock.advance(Duration.ofSeconds(31));

        assertEquals(Optional.empty(), service.consume(ticket));
        clock.advance(Duration.ofSeconds(-31)); // even if the clock went back, the ticket is gone
        assertEquals(Optional.empty(), service.consume(ticket));
    }

    @Test
    void unknownEmptyOrMissingTicketIsRefused() {
        service.issue(1L);

        assertEquals(Optional.empty(), service.consume("not-a-real-ticket"));
        assertEquals(Optional.empty(), service.consume(""));
        assertEquals(Optional.empty(), service.consume(null));
    }

    @Test
    void ticketBelongsToTheUserItWasIssuedTo() {
        String a = service.issue(1L);
        String b = service.issue(2L);

        assertEquals(Optional.of(2L), service.consume(b));
        assertEquals(Optional.of(1L), service.consume(a));
    }

    // 16 requests present the same ticket at the same moment, exactly one may get in
    @Test
    void sixteenThreadsRedeemingOneTicketHaveExactlyOneWinner() throws Exception {
        int threads = 16;
        String ticket = service.issue(7L);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Optional<Long>>> results = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return service.consume(ticket);
            }));
        }

        ready.await();
        go.countDown();

        int winners = 0;
        for (Future<Optional<Long>> result : results) {
            Optional<Long> userId = result.get(10, TimeUnit.SECONDS);
            if (userId.isPresent()) {
                winners++;
                assertEquals(7L, userId.get());
            }
        }
        pool.shutdown();

        assertEquals(1, winners);
    }
}
