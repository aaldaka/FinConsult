package com.example.notificationservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Optional;

/**
 * Validates the HMAC-signed JWTs issued by the main app. Same Base64 secret and the same
 * parsing as the main app, but the caller is read from the claims, never from a database.
 */
@Slf4j
@Component
public class JWTUtils {

    @Value("${jwt.secret}")
    private String jwtSecret;

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Checks the signature and the expiry, then reads the user id and the role from the claims.
     *
     * @return the caller, or empty if the token is invalid, expired or has no userId or role claim
     */
    public Optional<AuthenticatedUser> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Object userId = claims.get("userId");
            String role = claims.get("role", String.class);
            if (!(userId instanceof Number) || role == null || role.isBlank()) {
                log.warn("JWT rejected: the userId or role claim is missing");
                return Optional.empty();
            }
            return Optional.of(new AuthenticatedUser(((Number) userId).longValue(), role));
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT rejected: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
