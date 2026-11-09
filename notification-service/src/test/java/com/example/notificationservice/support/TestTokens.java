package com.example.notificationservice.support;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import java.util.Date;

/** Builds real signed tokens the way the main app does, for the tests. */
public final class TestTokens {

    // a second test-only secret (Base64 of 44 bytes), for tokens signed with the wrong key
    public static final String OTHER_SECRET = "YS1kaWZmZXJlbnQtdGVzdC1vbmx5LXNpZ25pbmcta2V5LTAxMjM0NTY3ODk=";

    private TestTokens() {
    }

    private static JwtBuilder base(long userId, String role, Date expiresAt) {
        return Jwts.builder()
                .subject("user" + userId + "@test.com")
                .claim("userId", userId)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(expiresAt);
    }

    private static String sign(JwtBuilder builder, String base64Secret) {
        return builder.signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret))).compact();
    }

    /** A token that is valid for an hour. */
    public static String valid(String base64Secret, long userId, String role) {
        return sign(base(userId, role, new Date(System.currentTimeMillis() + 3_600_000)), base64Secret);
    }

    public static String expired(String base64Secret, long userId, String role) {
        return sign(base(userId, role, new Date(System.currentTimeMillis() - 60_000)), base64Secret);
    }

    /** Signed correctly but without the userId claim, like a token from before the claims existed. */
    public static String withoutUserId(String base64Secret, String role) {
        return sign(Jwts.builder()
                .subject("old@test.com")
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000)), base64Secret);
    }
}
