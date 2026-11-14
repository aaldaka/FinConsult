package com.example.documentservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

// guards /internal/**, the endpoints other services call. The request must carry the header X-Internal-Key with the
// value of internal.api-key, anything else is a 403 and a user token does not help. Only this filter grants the
// authority the /internal/** rule asks for, so the key opens nothing outside /internal/**. With no key configured
// nothing matches, so the endpoints stay closed
@Slf4j
@Component
public class InternalKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Key";
    public static final String AUTHORITY = "INTERNAL_SERVICE";

    private final byte[] expectedDigest; // null when no key is configured

    public InternalKeyFilter(@Value("${internal.api-key:}") String apiKey) {
        this.expectedDigest = (apiKey == null || apiKey.isBlank()) ? null : sha256(apiKey);
        if (expectedDigest == null) {
            log.warn("internal.api-key is not set: every /internal/** request will be refused");
        }
    }

    static boolean isInternalPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/internal") || path.startsWith("/internal/");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isInternalPath(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!matches(request.getHeader(HEADER))) {
            log.warn("Refused {} {}: missing or wrong internal key", request.getMethod(), request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getOutputStream().write(
                    "{\"status\":403,\"error\":\"FORBIDDEN\",\"message\":\"Missing or invalid internal key\"}"
                            .getBytes(StandardCharsets.UTF_8));
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "internal-service", null, List.of(new SimpleGrantedAuthority(AUTHORITY))));
        filterChain.doFilter(request, response);
    }

    // both sides are hashed first so the comparison always sees 32 bytes, and isEqual does not stop at the first difference
    private boolean matches(String given) {
        if (expectedDigest == null || given == null) {
            return false;
        }
        return MessageDigest.isEqual(expectedDigest, sha256(given));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // every JVM has SHA-256
        }
    }
}
