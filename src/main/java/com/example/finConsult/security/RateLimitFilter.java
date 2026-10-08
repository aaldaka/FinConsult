package com.example.finConsult.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP rate limiting for brute-force-prone endpoints (login, register,
 * forget-password, reset-password), using an in-memory token bucket per client IP.
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final Set<String> LIMITED = Set.of( //limited endpoints
            "/auth/login", "/auth/register", "/auth/forget-password", "/auth/reset-password");

    // one bucket per client IP (in memory, resets on restart)
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(5) // 5 req per minute
                        .refillIntervally(5, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) { //only run the limited chosen endpoints (prevents bruteforcing)
        return !LIMITED.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        Bucket bucket = buckets.computeIfAbsent(ip, k -> newBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }

        long retryAfter = (long) Math.ceil(probe.getNanosToWaitForRefill() / 1_000_000_000.0); //convert to seconds + round up
        log.warn("Rate limit exceeded for {} on {}", ip, request.getRequestURI());
        response.setStatus(429); // too many requests
        response.setHeader("Retry-After", String.valueOf(retryAfter));
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"error\": \"Too many requests. Try again in " + retryAfter + " seconds.\"}");
    }
}