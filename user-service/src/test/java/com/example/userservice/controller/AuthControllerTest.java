package com.example.userservice.controller;

import com.example.userservice.model.PasswordResetToken;
import com.example.userservice.model.Profile;
import com.example.userservice.model.User;
import com.example.userservice.model.VerificationToken;
import com.example.userservice.model.enums.UserRole;
import com.example.userservice.model.enums.UserStatus;
import com.example.userservice.model.request.PasswordRules;
import com.example.userservice.repository.PasswordResetTokenRepository;
import com.example.userservice.repository.ProfileRepository;
import com.example.userservice.repository.UserRepository;
import com.example.userservice.repository.VerificationTokenRepository;
import com.example.userservice.security.JWTUtils;
import com.example.userservice.service.EmailService;
import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// runs on finconsult_test (TEST_DB_URL), which the main app's tests build. Every user here is created by the
// test with a unique email and deleted afterwards, real users are never touched. EmailService is mocked,
// so no mail is sent
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class AuthControllerTest {

    private static final String PASSWORD = "Str0ngP@ss!";
    private static final String NEW_PASSWORD = "N3wStr0ng#Pass";

    // every test gets its own fake client ip, so the rate limiter of one test never blocks another
    private static final AtomicInteger NEXT_IP = new AtomicInteger();

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired ProfileRepository profileRepository;
    @Autowired VerificationTokenRepository verificationTokenRepository;
    @Autowired PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JWTUtils jwtUtils;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean EmailService emailService;

    @Value("${jwt.secret}") String jwtSecret;

    private final List<String> createdEmails = new ArrayList<>();
    private String ip;

    @BeforeEach
    void useAFreshIp() {
        int n = NEXT_IP.incrementAndGet();
        ip = "10.20." + (n / 250) + "." + (n % 250 + 1);
    }

    // delete only the users these tests created (by their unique emails) and what hangs off them
    @AfterEach
    void deleteWhatThisTestCreated() {
        for (String email : createdEmails) {
            jdbcTemplate.update("DELETE FROM verification_tokens WHERE user_id IN (SELECT id FROM users WHERE email = ?)", email);
            jdbcTemplate.update("DELETE FROM password_reset_tokens WHERE user_id IN (SELECT id FROM users WHERE email = ?)", email);
            jdbcTemplate.update("DELETE FROM profiles WHERE user_id IN (SELECT id FROM users WHERE email = ?)", email);
            jdbcTemplate.update("DELETE FROM users WHERE email = ?", email);
        }
    }

    // helpers

    private String newEmail() {
        String email = "auth-test-" + UUID.randomUUID() + "@test.com";
        createdEmails.add(email);
        return email;
    }

    private User createUser(UserStatus status, boolean blacklisted) {
        User user = new User();
        user.setEmail(newEmail());
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setRole(UserRole.CLIENT);
        user.setUserStatus(status);
        user.setBlacklisted(blacklisted);
        user = userRepository.save(user);

        Profile profile = new Profile();
        profile.setUser(user);
        profile.setFirstName("Test");
        profile.setLastName("User");
        profile.setProfilePicturePath("/default.jpg");
        profileRepository.save(profile);
        return user;
    }

    private VerificationToken createVerificationToken(User user, LocalDateTime expiresAt, boolean used) {
        VerificationToken token = new VerificationToken();
        token.setUser(user);
        token.setToken(UUID.randomUUID().toString());
        token.setExpiresAt(expiresAt);
        token.setUsed(used);
        return verificationTokenRepository.save(token);
    }

    private PasswordResetToken createResetToken(User user, LocalDateTime expiresAt, boolean used) {
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setToken(UUID.randomUUID().toString());
        token.setExpiresAt(expiresAt);
        token.setUsed(used);
        return passwordResetTokenRepository.save(token);
    }

    private static String json(String... keyValues) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < keyValues.length; i += 2) {
            if (i > 0) sb.append(",");
            sb.append('"').append(keyValues[i]).append("\":\"").append(keyValues[i + 1]).append('"');
        }
        return sb.append("}").toString();
    }

    private RequestPostProcessor fromThisIp() {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private ResultActions postJson(String url, String body) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body).with(fromThisIp()));
    }

    private int count(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    // register

    @Test
    void registerCreatesAnUnverifiedClientAndSendsTheVerificationEmail() throws Exception {
        String email = newEmail();

        postJson("/auth/register", json("email", email, "password", PASSWORD, "firstName", "Jane", "lastName", "Doe"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.firstName").value("Jane"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.role").value("CLIENT"))
                .andExpect(jsonPath("$.token").doesNotExist());

        User saved = userRepository.findByEmail(email).orElseThrow();
        assertEquals(UserStatus.UNVERIFIED, saved.getUserStatus());
        assertEquals(UserRole.CLIENT, saved.getRole());
        assertNotEquals(PASSWORD, saved.getPassword());
        assertTrue(passwordEncoder.matches(PASSWORD, saved.getPassword()));
        assertEquals("/default.jpg", profileRepository.findByUser(saved).orElseThrow().getProfilePicturePath());
        assertEquals(1, count("SELECT COUNT(*) FROM verification_tokens WHERE user_id = ?", saved.getId()));
        verify(emailService).sendEmail(eq(email), eq("Verify your FinConsult email"),
                contains("/verify-email?token="), eq("verification"));
    }

    @Test
    void registerWithAnEmailThatIsAlreadyUsedIsA409() throws Exception {
        User existing = createUser(UserStatus.ACTIVE, false);

        postJson("/auth/register", json("email", existing.getEmail(), "password", PASSWORD, "firstName", "Jane", "lastName", "Doe"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("This email is already in use."));

        assertEquals(1, count("SELECT COUNT(*) FROM users WHERE email = ?", existing.getEmail()));
        verifyNoInteractions(emailService);
    }

    @Test
    void registerWithAWeakPasswordIsA400AndCreatesNothing() throws Exception {
        String email = newEmail();

        postJson("/auth/register", json("email", email, "password", "password1", "firstName", "Jane", "lastName", "Doe"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("password: " + PasswordRules.MESSAGE));

        assertEquals(0, count("SELECT COUNT(*) FROM users WHERE email = ?", email));
        verifyNoInteractions(emailService);
    }

    // login

    @Test
    void loginReturnsATokenWithTheUserIdAndRole() throws Exception {
        User user = createUser(UserStatus.ACTIVE, false);

        String body = postJson("/auth/login", json("email", user.getEmail(), "password", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.firstName").value("Test"))
                .andExpect(jsonPath("$.lastName").value("User"))
                .andExpect(jsonPath("$.role").value("CLIENT"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String token = JsonPath.read(body, "$.token");
        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret)))
                .build().parseSignedClaims(token).getPayload();
        assertEquals(user.getEmail(), claims.getSubject());
        assertEquals(user.getId(), ((Number) claims.get("userId")).longValue());
        assertEquals("CLIENT", claims.get("role", String.class));
    }

    @Test
    void loginWithAWrongPasswordIsA401() throws Exception {
        User user = createUser(UserStatus.ACTIVE, false);

        postJson("/auth/login", json("email", user.getEmail(), "password", "Wr0ng#Password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Entered password is incorrect."))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void loginWithAnUnknownEmailIsA401() throws Exception {
        postJson("/auth/login", json("email", newEmail(), "password", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid Email or User does not exist."));
    }

    @Test
    void loginOfAnInactiveAccountIsA403() throws Exception {
        User user = createUser(UserStatus.INACTIVE, false);

        postJson("/auth/login", json("email", user.getEmail(), "password", PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("This account has been deactivated"));
    }

    @Test
    void loginOfAnUnverifiedAccountIsA403() throws Exception {
        User user = createUser(UserStatus.UNVERIFIED, false);

        postJson("/auth/login", json("email", user.getEmail(), "password", PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Please verify your email."));
    }

    @Test
    void loginOfABlacklistedAccountIsA401EvenWithTheRightPassword() throws Exception {
        User user = createUser(UserStatus.ACTIVE, true);

        postJson("/auth/login", json("email", user.getEmail(), "password", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("This account is blocked from logging in"))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    // verify email

    @Test
    void verifyEmailActivatesTheAccountAndUsesTheToken() throws Exception {
        User user = createUser(UserStatus.UNVERIFIED, false);
        VerificationToken token = createVerificationToken(user, LocalDateTime.now().plusHours(1), false);

        mockMvc.perform(get("/auth/verify-email").param("token", token.getToken()))
                .andExpect(status().isOk())
                .andExpect(content().string("Email verified successfully."));

        assertEquals(UserStatus.ACTIVE, userRepository.findById(user.getId()).orElseThrow().getUserStatus());
        assertTrue(verificationTokenRepository.findByToken(token.getToken()).orElseThrow().isUsed());
    }

    @Test
    void verifyEmailWithAnExpiredTokenIsA401AndChangesNothing() throws Exception {
        User user = createUser(UserStatus.UNVERIFIED, false);
        VerificationToken token = createVerificationToken(user, LocalDateTime.now().minusMinutes(1), false);

        mockMvc.perform(get("/auth/verify-email").param("token", token.getToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("This verification link has expired. Resend another request."));

        assertEquals(UserStatus.UNVERIFIED, userRepository.findById(user.getId()).orElseThrow().getUserStatus());
    }

    @Test
    void verifyEmailWithAnAlreadyUsedTokenIsA401() throws Exception {
        User user = createUser(UserStatus.UNVERIFIED, false);
        VerificationToken token = createVerificationToken(user, LocalDateTime.now().plusHours(1), true);

        mockMvc.perform(get("/auth/verify-email").param("token", token.getToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("This verification link has been used."));

        assertEquals(UserStatus.UNVERIFIED, userRepository.findById(user.getId()).orElseThrow().getUserStatus());
    }

    @Test
    void verifyEmailWithAnUnknownTokenIsA404() throws Exception {
        mockMvc.perform(get("/auth/verify-email").param("token", "not-a-real-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Invalid verification token."));
    }

    @Test
    void verifyEmailWithoutATokenIsA400() throws Exception {
        mockMvc.perform(get("/auth/verify-email"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing parameter token"));
    }

    // forgot password

    @Test
    void forgotPasswordCreatesAResetTokenAndSendsTheEmail() throws Exception {
        User user = createUser(UserStatus.ACTIVE, false);

        postJson("/auth/forget-password", json("email", user.getEmail()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("A password reset link has been sent to the designated email."));

        List<PasswordResetToken> tokens = passwordResetTokenRepository.findByUserAndUsedFalse(user);
        assertEquals(1, tokens.size());
        LocalDateTime expiry = tokens.get(0).getExpiresAt();
        assertTrue(expiry.isAfter(LocalDateTime.now().plusMinutes(4)) && expiry.isBefore(LocalDateTime.now().plusMinutes(6)));
        verify(emailService).sendEmail(eq(user.getEmail()), eq("Reset your FinConsult password"),
                contains("/reset-password?token=" + tokens.get(0).getToken()), eq("password-reset"));
    }

    @Test
    void forgotPasswordForAnUnknownEmailGivesTheSameAnswerAndSendsNothing() throws Exception {
        postJson("/auth/forget-password", json("email", newEmail()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("A password reset link has been sent to the designated email."));

        verifyNoInteractions(emailService);
    }

    // reset password

    @Test
    void resetPasswordSetsTheNewPasswordAndInvalidatesEveryOtherToken() throws Exception {
        User user = createUser(UserStatus.ACTIVE, false);
        PasswordResetToken token = createResetToken(user, LocalDateTime.now().plusMinutes(5), false);
        PasswordResetToken older = createResetToken(user, LocalDateTime.now().plusMinutes(3), false);

        postJson("/auth/reset-password", json("token", token.getToken(), "newPassword", NEW_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("Password has been reset. You can now log in."));

        User saved = userRepository.findById(user.getId()).orElseThrow();
        assertTrue(passwordEncoder.matches(NEW_PASSWORD, saved.getPassword()));
        assertFalse(passwordEncoder.matches(PASSWORD, saved.getPassword()));
        assertTrue(passwordResetTokenRepository.findByToken(token.getToken()).orElseThrow().isUsed());
        assertTrue(passwordResetTokenRepository.findByToken(older.getToken()).orElseThrow().isUsed());
    }

    @Test
    void resetPasswordWithAnUnknownExpiredOrUsedTokenGivesTheSame400() throws Exception {
        User user = createUser(UserStatus.ACTIVE, false);
        PasswordResetToken expired = createResetToken(user, LocalDateTime.now().minusMinutes(1), false);
        PasswordResetToken used = createResetToken(user, LocalDateTime.now().plusMinutes(5), true);

        for (String token : List.of("not-a-real-token", expired.getToken(), used.getToken())) {
            postJson("/auth/reset-password", json("token", token, "newPassword", NEW_PASSWORD))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Invalid or expired reset token"));
        }

        assertTrue(passwordEncoder.matches(PASSWORD, userRepository.findById(user.getId()).orElseThrow().getPassword()));
    }

    @Test
    void resetPasswordWithAWeakPasswordIsA400() throws Exception {
        User user = createUser(UserStatus.ACTIVE, false);
        PasswordResetToken token = createResetToken(user, LocalDateTime.now().plusMinutes(5), false);

        postJson("/auth/reset-password", json("token", token.getToken(), "newPassword", "password1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("newPassword: " + PasswordRules.MESSAGE));

        assertFalse(passwordResetTokenRepository.findByToken(token.getToken()).orElseThrow().isUsed());
    }

    @Test
    void resetPasswordOfADeactivatedAccountIsA403() throws Exception {
        User user = createUser(UserStatus.INACTIVE, false);
        PasswordResetToken token = createResetToken(user, LocalDateTime.now().plusMinutes(5), false);

        postJson("/auth/reset-password", json("token", token.getToken(), "newPassword", NEW_PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("This account has been deactivated"));
    }

    // change password

    private String bearer(User user) {
        return "Bearer " + jwtUtils.generateToken(user);
    }

    @Test
    void changePasswordSetsTheNewPassword() throws Exception {
        User user = createUser(UserStatus.ACTIVE, false);

        mockMvc.perform(post("/auth/change-password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("Password changed successfully."));

        assertTrue(passwordEncoder.matches(NEW_PASSWORD, userRepository.findById(user.getId()).orElseThrow().getPassword()));
    }

    @Test
    void changePasswordWithAWrongCurrentPasswordIsA401AndChangesNothing() throws Exception {
        User user = createUser(UserStatus.ACTIVE, false);

        mockMvc.perform(post("/auth/change-password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("currentPassword", "Wr0ng#Password", "newPassword", NEW_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Entered password (current) is incorrect."));

        assertTrue(passwordEncoder.matches(PASSWORD, userRepository.findById(user.getId()).orElseThrow().getPassword()));
    }

    @Test
    void changePasswordWithAWeakNewPasswordIsA400() throws Exception {
        User user = createUser(UserStatus.ACTIVE, false);

        mockMvc.perform(post("/auth/change-password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("currentPassword", PASSWORD, "newPassword", "password1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("newPassword: " + PasswordRules.MESSAGE));
    }

    @Test
    void changePasswordNeedsALogin() throws Exception {
        mockMvc.perform(post("/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aBlacklistedUsersTokenIsRefusedOnTheNextRequest() throws Exception {
        User user = createUser(UserStatus.ACTIVE, true);

        mockMvc.perform(post("/auth/change-password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    // rate limiter, wired into the real security chain

    @Test
    void theSixthLoginAttemptFromOneIpIsA429() throws Exception {
        String body = json("email", newEmail(), "password", PASSWORD);

        for (int i = 1; i <= 5; i++) {
            postJson("/auth/login", body).andExpect(status().isUnauthorized());
        }

        postJson("/auth/login", body)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.startsWith("Too many requests")));
    }
}
