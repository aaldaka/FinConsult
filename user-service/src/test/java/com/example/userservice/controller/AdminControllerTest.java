package com.example.userservice.controller;

import com.example.userservice.integration.AppointmentCancellation;
import com.example.userservice.integration.AuditRecorder;
import com.example.userservice.integration.EmailPublisher;
import com.example.userservice.model.Advisor;
import com.example.userservice.model.Profile;
import com.example.userservice.model.User;
import com.example.userservice.model.enums.AuditAction;
import com.example.userservice.model.enums.Specialty;
import com.example.userservice.model.enums.UserRole;
import com.example.userservice.model.enums.UserStatus;
import com.example.userservice.model.request.PasswordRules;
import com.example.userservice.repository.AdvisorRepository;
import com.example.userservice.repository.ProfileRepository;
import com.example.userservice.repository.UserRepository;
import com.example.userservice.security.JWTUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// runs on finconsult_test (TEST_DB_URL), which docker/db/init/00-create-test-db.sh builds. Every user here is created by the
// test (or by the endpoint under test) with a unique email and deleted afterwards, real users are never touched.
// The email publisher and the two cross-area points (appointments and the audit log) are mocked, so we can check
// exactly when they are called
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class AdminControllerTest {

    private static final String PASSWORD = "Str0ngP@ss!";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired ProfileRepository profileRepository;
    @Autowired AdvisorRepository advisorRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JWTUtils jwtUtils;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean EmailPublisher emailPublisher;
    @MockitoBean AppointmentCancellation appointmentCancellation;
    @MockitoBean AuditRecorder auditRecorder;

    private final List<String> createdEmails = new ArrayList<>();

    // delete only the users these tests created (by their unique emails) and what hangs off them
    @AfterEach
    void deleteWhatThisTestCreated() {
        for (String email : createdEmails) {
            jdbcTemplate.update("DELETE FROM advisors WHERE user_id IN (SELECT id FROM users WHERE email = ?)", email);
            jdbcTemplate.update("DELETE FROM verification_tokens WHERE user_id IN (SELECT id FROM users WHERE email = ?)", email);
            jdbcTemplate.update("DELETE FROM password_reset_tokens WHERE user_id IN (SELECT id FROM users WHERE email = ?)", email);
            jdbcTemplate.update("DELETE FROM profiles WHERE user_id IN (SELECT id FROM users WHERE email = ?)", email);
            jdbcTemplate.update("DELETE FROM users WHERE email = ?", email);
        }
    }

    // helpers

    private String newEmail() {
        String email = "admin-test-" + UUID.randomUUID() + "@test.com";
        createdEmails.add(email);
        return email;
    }

    private User createUser(UserRole role, UserStatus status) {
        User user = new User();
        user.setEmail(newEmail());
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setUserStatus(status);
        user = userRepository.save(user);

        Profile profile = new Profile();
        profile.setUser(user);
        profile.setFirstName("Test");
        profile.setLastName("Person");
        profile.setProfilePicturePath("/default.jpg");
        profileRepository.save(profile);
        return user;
    }

    private Advisor createAdvisor(User user) {
        Advisor advisor = new Advisor();
        advisor.setUser(user);
        advisor.setTitle("Test Advisor");
        advisor.setSpecialty(Specialty.INSURANCE);
        advisor.setYearsOfExperience(4);
        advisor.setActive(true);
        return advisorRepository.save(advisor);
    }

    private String bearer(User user) {
        return "Bearer " + jwtUtils.generateToken(user);
    }

    private String advisorJson(String email, String password, String extra) {
        return "{\"email\": \"" + email + "\", \"password\": \"" + password + "\", \"firstName\": \"Created\", "
                + "\"lastName\": \"Advisor\", \"title\": \"Planner\"" + extra + "}";
    }

    // create advisor

    @Test
    void anAdminCreatesAnAdvisorWithAProfileAndNoEmail() throws Exception {
        User admin = createUser(UserRole.ADMIN, UserStatus.ACTIVE);
        String email = newEmail();

        mockMvc.perform(post("/admin/advisors")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(advisorJson(email, PASSWORD, ", \"specialty\": \"LOANS\", \"yearsOfExperience\": 5")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Created Advisor"))
                .andExpect(jsonPath("$.title").value("Planner"))
                .andExpect(jsonPath("$.specialty").value("LOANS"))
                .andExpect(jsonPath("$.yearsOfExperience").value(5))
                .andExpect(jsonPath("$.active").value(true));

        User created = userRepository.findByEmail(email).orElseThrow();
        assertEquals(UserRole.ADVISOR, created.getRole());
        assertEquals(UserStatus.ACTIVE, created.getUserStatus()); // admin-created, no verification step
        assertTrue(passwordEncoder.matches(PASSWORD, created.getPassword()));
        Profile profile = profileRepository.findByUser(created).orElseThrow();
        assertEquals("Created", profile.getFirstName());
        assertEquals("Advisor", profile.getLastName());
        assertEquals("/default.jpg", profile.getProfilePicturePath());
        Advisor advisor = advisorRepository.findByUser(created).orElseThrow();
        assertTrue(advisor.isActive());

        // the monolith sends no email for an admin-created advisor
        verifyNoInteractions(emailPublisher);
        verify(auditRecorder).record(eq(admin.getId()), eq(AuditAction.ADVISOR_CREATED), eq("Advisor"),
                eq(advisor.getId()), eq("Created advisor account for " + email));
    }

    @Test
    void theSpecialtyDefaultsToGeneral() throws Exception {
        User admin = createUser(UserRole.ADMIN, UserStatus.ACTIVE);
        String email = newEmail();

        mockMvc.perform(post("/admin/advisors")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(advisorJson(email, PASSWORD, "")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.specialty").value("GENERAL"))
                .andExpect(jsonPath("$.yearsOfExperience").value(0));
    }

    @Test
    void creatingAnAdvisorWithAUsedEmailIsA409() throws Exception {
        User admin = createUser(UserRole.ADMIN, UserStatus.ACTIVE);
        User existing = createUser(UserRole.CLIENT, UserStatus.ACTIVE);

        mockMvc.perform(post("/admin/advisors")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(advisorJson(existing.getEmail(), PASSWORD, "")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An account with this email already exists"));

        verifyNoInteractions(auditRecorder);
    }

    @Test
    void creatingAnAdvisorWithAWeakPasswordOrBadValuesIsA400() throws Exception {
        User admin = createUser(UserRole.ADMIN, UserStatus.ACTIVE);

        mockMvc.perform(post("/admin/advisors")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(advisorJson(newEmail(), "password1", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("password: " + PasswordRules.MESSAGE));

        mockMvc.perform(post("/admin/advisors")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(advisorJson(newEmail(), PASSWORD, ", \"yearsOfExperience\": -1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(startsWith("yearsOfExperience: ")));

        verifyNoInteractions(auditRecorder);
    }

    // clients picker

    @Test
    void theClientsListHasActiveClientsOnly() throws Exception {
        User admin = createUser(UserRole.ADMIN, UserStatus.ACTIVE);
        User active = createUser(UserRole.CLIENT, UserStatus.ACTIVE);
        User inactive = createUser(UserRole.CLIENT, UserStatus.INACTIVE);
        User advisor = createUser(UserRole.ADVISOR, UserStatus.ACTIVE);

        mockMvc.perform(get("/admin/clients").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + active.getId() + ")]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.id == " + active.getId() + ")].name").value(hasItem("Test Person")))
                .andExpect(jsonPath("$[?(@.id == " + active.getId() + ")].email").value(hasItem(active.getEmail())))
                .andExpect(jsonPath("$[?(@.id == " + inactive.getId() + ")]").isEmpty())
                .andExpect(jsonPath("$[?(@.id == " + advisor.getId() + ")]").isEmpty());
    }

    // deactivate a user

    @Test
    void deactivatingAClientSetsItInactiveAndAsksForTheCancellations() throws Exception {
        User admin = createUser(UserRole.ADMIN, UserStatus.ACTIVE);
        User client = createUser(UserRole.CLIENT, UserStatus.ACTIVE);

        mockMvc.perform(patch("/admin/users/{id}/deactivate", client.getId()).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("User deactivated"));

        assertEquals(UserStatus.INACTIVE, userRepository.findById(client.getId()).orElseThrow().getUserStatus());
        verify(auditRecorder).record(eq(admin.getId()), eq(AuditAction.USER_DEACTIVATED), eq("User"),
                eq(client.getId()), eq("Account deactivated"));
        verify(appointmentCancellation).cancelUpcomingOfClient(eq(client.getId()), eq(admin.getId()), eq("Client account was deactivated"));
    }

    @Test
    void deactivatingAnUnknownUserIsA404() throws Exception {
        User admin = createUser(UserRole.ADMIN, UserStatus.ACTIVE);

        mockMvc.perform(patch("/admin/users/{id}/deactivate", Long.MAX_VALUE).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));

        verifyNoInteractions(auditRecorder, appointmentCancellation);
    }

    @Test
    void anAdvisorCannotBeDeactivatedThroughTheUserEndpoint() throws Exception {
        User admin = createUser(UserRole.ADMIN, UserStatus.ACTIVE);
        User advisor = createUser(UserRole.ADVISOR, UserStatus.ACTIVE);

        mockMvc.perform(patch("/admin/users/{id}/deactivate", advisor.getId()).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Deactivate advisors through the advisor deactivation endpoint instead"));

        assertEquals(UserStatus.ACTIVE, userRepository.findById(advisor.getId()).orElseThrow().getUserStatus());
        verifyNoInteractions(auditRecorder, appointmentCancellation);
    }

    // deactivate an advisor

    @Test
    void deactivatingAnAdvisorSetsBothInactiveAndAsksForTheCancellations() throws Exception {
        User admin = createUser(UserRole.ADMIN, UserStatus.ACTIVE);
        User advisorUser = createUser(UserRole.ADVISOR, UserStatus.ACTIVE);
        Advisor advisor = createAdvisor(advisorUser);

        mockMvc.perform(patch("/admin/advisors/{id}/deactivate", advisor.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"left the company\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("Advisor deactivated"));

        assertFalse(advisorRepository.findById(advisor.getId()).orElseThrow().isActive());
        assertEquals(UserStatus.INACTIVE, userRepository.findById(advisorUser.getId()).orElseThrow().getUserStatus());
        verify(auditRecorder).record(eq(admin.getId()), eq(AuditAction.ADVISOR_DEACTIVATED), eq("Advisor"),
                eq(advisor.getId()), eq("Reason: left the company"));
        verify(appointmentCancellation).cancelUpcomingOfAdvisor(eq(advisor.getId()), eq(advisorUser.getId()),
                eq("Advisor is no longer available: left the company"));
    }

    @Test
    void deactivatingAnAdvisorNeedsAReason() throws Exception {
        User admin = createUser(UserRole.ADMIN, UserStatus.ACTIVE);
        Advisor advisor = createAdvisor(createUser(UserRole.ADVISOR, UserStatus.ACTIVE));

        for (String body : List.of("{}", "{\"reason\": \"   \"}")) {
            mockMvc.perform(patch("/admin/advisors/{id}/deactivate", advisor.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("A reason is required"));
        }

        mockMvc.perform(patch("/admin/advisors/{id}/deactivate", advisor.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request body"));

        assertTrue(advisorRepository.findById(advisor.getId()).orElseThrow().isActive());
        verifyNoInteractions(auditRecorder, appointmentCancellation);
    }

    @Test
    void deactivatingAnUnknownAdvisorIsA404() throws Exception {
        User admin = createUser(UserRole.ADMIN, UserStatus.ACTIVE);

        mockMvc.perform(patch("/admin/advisors/{id}/deactivate", Long.MAX_VALUE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"x\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Advisor not found"));

        verifyNoInteractions(auditRecorder, appointmentCancellation);
    }

    // roles: only an admin may use these endpoints

    @Test
    void aClientOrAnAdvisorCallingAnAdminEndpointGetsA403() throws Exception {
        User client = createUser(UserRole.CLIENT, UserStatus.ACTIVE);
        User advisor = createUser(UserRole.ADVISOR, UserStatus.ACTIVE);
        User target = createUser(UserRole.CLIENT, UserStatus.ACTIVE);

        for (User caller : List.of(client, advisor)) {
            mockMvc.perform(post("/admin/advisors")
                            .header(HttpHeaders.AUTHORIZATION, bearer(caller))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(advisorJson(newEmail(), PASSWORD, "")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                    .andExpect(jsonPath("$.message").value("You do not have permission to access this resource"));

            mockMvc.perform(get("/admin/clients").header(HttpHeaders.AUTHORIZATION, bearer(caller)))
                    .andExpect(status().isForbidden());

            mockMvc.perform(patch("/admin/users/{id}/deactivate", target.getId()).header(HttpHeaders.AUTHORIZATION, bearer(caller)))
                    .andExpect(status().isForbidden());

            mockMvc.perform(patch("/admin/advisors/{id}/deactivate", 1L)
                            .header(HttpHeaders.AUTHORIZATION, bearer(caller))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\": \"x\"}"))
                    .andExpect(status().isForbidden());
        }

        assertEquals(UserStatus.ACTIVE, userRepository.findById(target.getId()).orElseThrow().getUserStatus());
        verifyNoInteractions(auditRecorder, appointmentCancellation);
    }

    @Test
    void theAdminEndpointsNeedALogin() throws Exception {
        mockMvc.perform(post("/admin/advisors").contentType(MediaType.APPLICATION_JSON).content(advisorJson(anyEmail(), PASSWORD, "")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/clients")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/admin/users/1/deactivate")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/admin/advisors/1/deactivate").contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    // an email that is never saved, so it needs no cleanup
    private String anyEmail() {
        return "admin-test-unsaved-" + UUID.randomUUID() + "@test.com";
    }
}
