package com.example.userservice.security;

import com.example.userservice.model.User;
import com.example.userservice.model.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JWTUtilsTest {

    // test-only secret, Base64 of a 54 byte string
    private static final String SECRET = "ZmluY29uc3VsdC10ZXN0LW9ubHktand0LXNpZ25pbmcta2V5LW5vdC1hLXJlYWwtc2VjcmV0";

    private JWTUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JWTUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", 3_600_000L);
    }

    private User user(Long id, String email, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setRole(role);
        return user;
    }

    // reads the token the way another service would: with only the shared secret
    private Claims claimsOf(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Test
    void tokenCarriesEmailUserIdAndRole() {
        String token = jwtUtils.generateToken(user(7L, "reem@test.com", UserRole.ADVISOR));

        Claims claims = claimsOf(token);
        assertEquals("reem@test.com", claims.getSubject());
        assertEquals(7L, ((Number) claims.get("userId")).longValue());
        assertEquals("ADVISOR", claims.get("role", String.class));
    }

    @Test
    void roleIsTheRoleNameForEveryRole() {
        for (UserRole role : UserRole.values()) {
            String token = jwtUtils.generateToken(user(1L, "a@test.com", role));
            assertEquals(role.name(), claimsOf(token).get("role", String.class));
        }
    }

    @Test
    void existingHelpersStillWorkWithTheNewClaims() {
        String token = jwtUtils.generateToken(user(3L, "maryam@test.com", UserRole.CLIENT));

        assertTrue(jwtUtils.validateJwtToken(token));
        assertEquals("maryam@test.com", jwtUtils.getUserNameFromJwtToken(token));
    }

    @Test
    void tamperedClaimsAreRejected() {
        String token = jwtUtils.generateToken(user(3L, "maryam@test.com", UserRole.CLIENT));
        String[] parts = token.split("\\.");
        // swap in the payload of an admin token, the signature no longer matches
        String adminPayload = jwtUtils.generateToken(user(1L, "maryam@test.com", UserRole.ADMIN)).split("\\.")[1];
        String forged = parts[0] + "." + adminPayload + "." + parts[2];

        assertFalse(jwtUtils.validateJwtToken(forged));
    }
}
