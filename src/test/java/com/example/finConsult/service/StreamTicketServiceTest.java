package com.example.finConsult.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

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

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
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
        String a = service.issue("a@test.com");
        String b = service.issue("a@test.com");

        assertNotEquals(a, b);
        assertEquals(32, Base64.getUrlDecoder().decode(a).length);
        assertTrue(a.matches("[A-Za-z0-9_-]+"));
    }

    @Test
    void ticketWorksOnce() {
        String ticket = service.issue("maryam@test.com");

        assertEquals(Optional.of("maryam@test.com"), service.consume(ticket));
        assertEquals(Optional.empty(), service.consume(ticket));
    }

    @Test
    void ticketWorksUpToThirtySecondsAndNotAfter() {
        String fresh = service.issue("a@test.com");
        String late = service.issue("b@test.com");

        clock.advance(Duration.ofSeconds(30));
        assertEquals(Optional.of("a@test.com"), service.consume(fresh));

        clock.advance(Duration.ofSeconds(1));
        assertEquals(Optional.empty(), service.consume(late));
    }

    @Test
    void expiredTicketCannotBeTriedAgain() {
        String ticket = service.issue("a@test.com");
        clock.advance(Duration.ofSeconds(31));

        assertEquals(Optional.empty(), service.consume(ticket));
        clock.advance(Duration.ofSeconds(-31)); // even if the clock went back, the ticket is gone
        assertEquals(Optional.empty(), service.consume(ticket));
    }

    @Test
    void unknownOrMissingTicketIsRefused() {
        service.issue("a@test.com");

        assertEquals(Optional.empty(), service.consume("not-a-real-ticket"));
        assertEquals(Optional.empty(), service.consume(""));
        assertEquals(Optional.empty(), service.consume(null));
    }

    @Test
    void ticketBelongsToTheUserItWasIssuedTo() {
        String a = service.issue("a@test.com");
        String b = service.issue("b@test.com");

        assertEquals(Optional.of("b@test.com"), service.consume(b));
        assertEquals(Optional.of("a@test.com"), service.consume(a));
    }

    // 16 requests present the same ticket at the same moment, exactly one may get in
    @Test
    void sixteenThreadsRedeemingOneTicketHaveExactlyOneWinner() throws Exception {
        int threads = 16;
        String ticket = service.issue("maryam@test.com");

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Optional<String>>> results = new ArrayList<>();

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
        for (Future<Optional<String>> result : results) {
            Optional<String> email = result.get(10, TimeUnit.SECONDS);
            if (email.isPresent()) {
                winners++;
                assertEquals("maryam@test.com", email.get());
            }
        }
        pool.shutdown();

        assertEquals(1, winners);
    }
}
