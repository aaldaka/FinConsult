package com.example.bookingservice.service;

import com.example.bookingservice.client.UserClient;
import com.example.bookingservice.client.UserInfo;
import com.example.bookingservice.exception.AccountNotActive;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AccountStatusService {

    // short, so a user who gets blacklisted is blocked within this time even with a token that has not expired
    static final Duration CACHE_TTL = Duration.ofSeconds(30);
    private static final int MAX_CACHED_USERS = 10_000;

    private record CachedUser(UserInfo user, long expiresAtMillis) {}

    private final UserClient userClient;
    private final Clock clock;
    private final Map<Long, CachedUser> cache = new ConcurrentHashMap<>();

    @Autowired
    public AccountStatusService(UserClient userClient) {
        this(userClient, Clock.systemUTC());
    }

    AccountStatusService(UserClient userClient, Clock clock) {
        this.userClient = userClient;
        this.clock = clock;
    }

    // call this before the database transaction of an action opens, never inside one, because it can make an http call.
    // a user service that does not answer is a 503 (the answer is not guessed from an old one), and an unknown user is a 404
    public void requireActive(Long userId) {
        UserInfo user = lookup(userId);
        if (user.blacklisted()) {
            log.warn("Refused user {}: blacklisted", userId);
            throw new AccountNotActive("Your account is blacklisted");
        }
        if (!"ACTIVE".equals(user.status())) {
            log.warn("Refused user {}: status {}", userId, user.status());
            throw new AccountNotActive("Your account is not active");
        }
    }

    // the user is cached, not the verdict, and only answers are cached, never failures
    private UserInfo lookup(Long userId) {
        long now = clock.millis();
        CachedUser cached = cache.get(userId);
        if (cached != null && cached.expiresAtMillis() > now) {
            return cached.user();
        }

        UserInfo user = userClient.getById(userId);

        if (cache.size() >= MAX_CACHED_USERS) {
            cache.values().removeIf(entry -> entry.expiresAtMillis() <= now); // make room by dropping the expired ones
        }
        if (cache.size() < MAX_CACHED_USERS) {
            cache.put(userId, new CachedUser(user, now + CACHE_TTL.toMillis()));
        }
        return user;
    }
}
