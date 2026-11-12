package com.example.bookingservice.security;

import com.example.bookingservice.support.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// no Spring and no database: this service only verifies tokens, the user service issues them
class JWTUtilsTest {

    // test-only secret, Base64 of a 54 byte string
    private static final String SECRET = "ZmluY29uc3VsdC10ZXN0LW9ubHktand0LXNpZ25pbmcta2V5LW5vdC1hLXJlYWwtc2VjcmV0";

    private JWTUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JWTUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", SECRET);
    }

    @Test
    void aValidTokenGivesTheUserIdAndTheRole() {
        Optional<AuthenticatedUser> user = jwtUtils.parse(TestTokens.valid(SECRET, 7L, "ADVISOR"));

        assertEquals(new AuthenticatedUser(7L, "ADVISOR"), user.orElseThrow());
    }

    @Test
    void theRoleIsReadAsItIsForEveryRole() {
        for (String role : new String[]{"CLIENT", "ADVISOR", "ADMIN"}) {
            assertEquals(role, jwtUtils.parse(TestTokens.valid(SECRET, 1L, role)).orElseThrow().role());
        }
    }

    @Test
    void anExpiredTokenIsRejected() {
        assertTrue(jwtUtils.parse(TestTokens.expired(SECRET, 7L, "CLIENT")).isEmpty());
    }

    @Test
    void aTokenSignedWithAnotherKeyIsRejected() {
        assertTrue(jwtUtils.parse(TestTokens.valid(TestTokens.OTHER_SECRET, 7L, "CLIENT")).isEmpty());
    }

    @Test
    void aTokenWithoutTheUserIdOrTheRoleClaimIsRejected() {
        assertTrue(jwtUtils.parse(TestTokens.withoutUserId(SECRET, "CLIENT")).isEmpty());
        assertTrue(jwtUtils.parse(TestTokens.withoutRole(SECRET, 7L)).isEmpty());
    }

    @Test
    void tamperedClaimsAreRejected() {
        String[] parts = TestTokens.valid(SECRET, 3L, "CLIENT").split("\\.");
        // swap in the payload of an admin token, the signature no longer matches
        String adminPayload = TestTokens.valid(SECRET, 3L, "ADMIN").split("\\.")[1];
        String forged = parts[0] + "." + adminPayload + "." + parts[2];

        assertTrue(jwtUtils.parse(forged).isEmpty());
    }

    @Test
    void garbageEmptyAndNullAreRejected() {
        assertTrue(jwtUtils.parse("not-a-token").isEmpty());
        assertTrue(jwtUtils.parse("").isEmpty());
        assertTrue(jwtUtils.parse(null).isEmpty());
    }
}
