package com.example.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One-time tickets for the notification stream. The browser's EventSource cannot send an
 * Authorization header, so instead of putting the JWT in the URL the client asks for a ticket
 * (with its JWT) and opens the stream with that. A ticket works once and only for 30 seconds.
 * <p>
 * Tickets live in memory, so with more than one instance of this service they would have to move
 * to a shared store (Redis).
 */
@Slf4j
@Service
public class StreamTicketService {

    private static final Duration TTL = Duration.ofSeconds(30);

    private record Ticket(Long userId, Instant expiresAt) {}

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public StreamTicketService() {
        this(Clock.systemUTC());
    }

    // the clock is only replaced in tests
    StreamTicketService(Clock clock) {
        this.clock = clock;
    }

    /** @return a random 32-byte ticket (Base64URL) for this user, valid for 30 seconds */
    public String issue(Long userId) {
        Instant now = clock.instant();
        tickets.values().removeIf(t -> now.isAfter(t.expiresAt())); // tickets nobody used would pile up

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        tickets.put(ticket, new Ticket(userId, now.plus(TTL)));
        log.debug("Stream ticket issued for user {}", userId);
        return ticket;
    }

    /**
     * Redeems a ticket. remove() is atomic, so when several requests present the same ticket at
     * once only one of them gets the user id back.
     *
     * @return the user id the ticket was issued to, or empty if it is unknown, used or expired
     */
    public Optional<Long> consume(String ticket) {
        if (ticket == null) {
            return Optional.empty();
        }
        Ticket found = tickets.remove(ticket);
        if (found == null || clock.instant().isAfter(found.expiresAt())) {
            return Optional.empty();
        }
        return Optional.of(found.userId());
    }
}
