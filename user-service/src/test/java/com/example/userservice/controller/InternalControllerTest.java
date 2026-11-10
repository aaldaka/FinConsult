package com.example.userservice.controller;

import com.example.userservice.integration.AppointmentCancellation;
import com.example.userservice.integration.AuditRecorder;
import com.example.userservice.model.Advisor;
import com.example.userservice.model.Profile;
import com.example.userservice.model.User;
import com.example.userservice.model.enums.Specialty;
import com.example.userservice.model.enums.UserRole;
import com.example.userservice.model.enums.UserStatus;
import com.example.userservice.repository.AdvisorRepository;
import com.example.userservice.repository.ProfileRepository;
import com.example.userservice.repository.UserRepository;
import com.example.userservice.security.JWTUtils;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// runs on finconsult_test (TEST_DB_URL), which the main app's tests build. Every user here is created by the
// test with a unique email and deleted afterwards, real users are never touched
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class InternalControllerTest {

    private static final String KEY_HEADER = "X-Internal-Key";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired ProfileRepository profileRepository;
    @Autowired AdvisorRepository advisorRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JWTUtils jwtUtils;
    @Autowired JdbcTemplate jdbcTemplate;

    @Value("${internal.api-key}") String internalKey;

    @MockitoBean AuditRecorder auditRecorder;
    @MockitoBean AppointmentCancellation appointmentCancellation;

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

    private User createUser(UserRole role, UserStatus userStatus, boolean withProfile) {
        User user = new User();
        user.setEmail("internal-test-" + UUID.randomUUID() + "@test.com");
        createdEmails.add(user.getEmail());
        user.setPassword(passwordEncoder.encode("Str0ngP@ss!"));
        user.setRole(role);
        user.setUserStatus(userStatus);
        user = userRepository.save(user);

        if (withProfile) {
            Profile profile = new Profile();
            profile.setUser(user);
            profile.setFirstName("Internal");
            profile.setLastName("Tester");
            profile.setProfilePicturePath("/default.jpg");
            profileRepository.save(profile);
        }
        return user;
    }

    private User createClient() {
        return createUser(UserRole.CLIENT, UserStatus.ACTIVE, true);
    }

    private Advisor createAdvisorFor(User user) {
        Advisor advisor = new Advisor();
        advisor.setUser(user);
        advisor.setTitle("Planner");
        advisor.setSpecialty(Specialty.GENERAL);
        advisor.setYearsOfExperience(4);
        advisor.setActive(true);
        return advisorRepository.save(advisor);
    }

    private MockHttpServletRequestBuilder withKey(MockHttpServletRequestBuilder builder) {
        return builder.header(KEY_HEADER, internalKey);
    }

    private User reload(User user) {
        return userRepository.findById(user.getId()).orElseThrow();
    }

    // protection

    @Test
    void withoutTheRightKeyEveryInternalEndpointIsForbidden() throws Exception {
        User client = createClient();
        String id = String.valueOf(client.getId());
        String promoteBody = "{\"userId\": " + id + ", \"title\": \"Planner\", \"specialty\": \"GENERAL\", \"yearsOfExperience\": 3}";

        List<MockHttpServletRequestBuilder> requests = List.of(
                get("/internal/users/" + id),
                post("/internal/users/names").contentType(MediaType.APPLICATION_JSON).content("[" + id + "]"),
                post("/internal/users/" + id + "/strikes"),
                delete("/internal/users/" + id + "/strikes"),
                get("/internal/users/admins"),
                get("/internal/advisors/1"),
                get("/internal/advisors/by-user/" + id),
                post("/internal/advisors/lookup").contentType(MediaType.APPLICATION_JSON).content("[1]"),
                post("/internal/advisors/promote").contentType(MediaType.APPLICATION_JSON).content(promoteBody));

        for (MockHttpServletRequestBuilder request : requests) {
            // no header, a wrong header
            mockMvc.perform(request).andExpect(status().isForbidden());
        }
        for (MockHttpServletRequestBuilder request : List.of(
                get("/internal/users/" + id),
                get("/internal/advisors/by-user/" + id),
                post("/internal/advisors/lookup").contentType(MediaType.APPLICATION_JSON).content("[1]"),
                post("/internal/users/" + id + "/strikes"),
                post("/internal/advisors/promote").contentType(MediaType.APPLICATION_JSON).content(promoteBody))) {
            mockMvc.perform(request.header(KEY_HEADER, "not-the-key")).andExpect(status().isForbidden());
        }

        // nothing happened behind the 403s
        assertEquals(0, reload(client).getNoShowStrikes());
        assertEquals(UserRole.CLIENT, reload(client).getRole());
        assertTrue(advisorRepository.findByUser(client).isEmpty());
    }

    @Test
    void aNormalUserTokenDoesNotOpenInternalEndpointsNotEvenAnAdminsOrWithTheKeyMissing() throws Exception {
        User client = createClient();
        User admin = createUser(UserRole.ADMIN, UserStatus.ACTIVE, true);

        for (User caller : List.of(client, admin)) {
            String bearer = "Bearer " + jwtUtils.generateToken(caller);
            mockMvc.perform(get("/internal/users/" + client.getId()).header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/internal/users/" + client.getId() + "/strikes").header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/internal/users/admins").header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isForbidden());
        }
        assertEquals(0, reload(client).getNoShowStrikes());
    }

    @Test
    void theKeyDoesNotOpenNormalEndpoints() throws Exception {
        mockMvc.perform(get("/profile/me").header(KEY_HEADER, internalKey)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/advisors").header(KEY_HEADER, internalKey)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/admin/users").header(KEY_HEADER, internalKey)).andExpect(status().isUnauthorized());
    }

    @Test
    void theKeyIsEnoughForInternalEndpointsEvenWithAUserTokenAlongside() throws Exception {
        User client = createClient();

        mockMvc.perform(withKey(get("/internal/users/" + client.getId()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtUtils.generateToken(client)))
                .andExpect(status().isOk());
    }

    // users

    @Test
    void getUserReturnsTheAccountAndTheDisplayName() throws Exception {
        User user = createClient();
        jdbcTemplate.update("UPDATE users SET no_show_strikes = 2 WHERE id = ?", user.getId());

        mockMvc.perform(withKey(get("/internal/users/" + user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().intValue()))
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.role").value("CLIENT"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.blacklisted").value(false))
                .andExpect(jsonPath("$.noShowStrikes").value(2))
                .andExpect(jsonPath("$.displayName").value("Internal Tester"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void getUserWithoutAProfileHasTheUnknownName() throws Exception {
        User user = createUser(UserRole.CLIENT, UserStatus.ACTIVE, false);

        mockMvc.perform(withKey(get("/internal/users/" + user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Unknown"));
    }

    @Test
    void getUnknownUserIs404() throws Exception {
        mockMvc.perform(withKey(get("/internal/users/" + Long.MAX_VALUE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void namesMapsIdsToDisplayNamesAndLeavesUnknownIdsOut() throws Exception {
        User first = createClient();
        User second = createUser(UserRole.CLIENT, UserStatus.ACTIVE, true);
        jdbcTemplate.update("UPDATE profiles SET first_name = 'Second' WHERE user_id = ?", second.getId());
        User noProfile = createUser(UserRole.CLIENT, UserStatus.ACTIVE, false);

        mockMvc.perform(withKey(post("/internal/users/names"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[" + first.getId() + ", " + second.getId() + ", " + noProfile.getId() + ", " + Long.MAX_VALUE + "]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['" + first.getId() + "']").value("Internal Tester"))
                .andExpect(jsonPath("$['" + second.getId() + "']").value("Second Tester"))
                .andExpect(jsonPath("$['" + noProfile.getId() + "']").value("Unknown"))
                .andExpect(jsonPath("$['" + Long.MAX_VALUE + "']").doesNotExist())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void namesWithAnEmptyListIsAnEmptyMapAndABadBodyIsA400() throws Exception {
        mockMvc.perform(withKey(post("/internal/users/names")).contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(withKey(post("/internal/users/names")).contentType(MediaType.APPLICATION_JSON).content("{\"ids\": [1]}"))
                .andExpect(status().isBadRequest());
    }

    // strikes

    @Test
    void strikesCountUpBlacklistAtThreeAndCountDownAgain() throws Exception {
        User user = createClient();
        String url = "/internal/users/" + user.getId() + "/strikes";

        mockMvc.perform(withKey(post(url)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noShowStrikes").value(1))
                .andExpect(jsonPath("$.blacklisted").value(false));
        mockMvc.perform(withKey(post(url)))
                .andExpect(jsonPath("$.noShowStrikes").value(2))
                .andExpect(jsonPath("$.blacklisted").value(false));
        mockMvc.perform(withKey(post(url)))
                .andExpect(jsonPath("$.noShowStrikes").value(3))
                .andExpect(jsonPath("$.blacklisted").value(true));

        User blacklisted = reload(user);
        assertEquals(3, blacklisted.getNoShowStrikes());
        assertTrue(blacklisted.isBlacklisted());

        mockMvc.perform(withKey(delete(url)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noShowStrikes").value(2))
                .andExpect(jsonPath("$.blacklisted").value(false));
        assertFalse(reload(user).isBlacklisted());
    }

    @Test
    void removingAStrikeNeverGoesBelowZero() throws Exception {
        User user = createClient();

        mockMvc.perform(withKey(delete("/internal/users/" + user.getId() + "/strikes")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noShowStrikes").value(0))
                .andExpect(jsonPath("$.blacklisted").value(false));
        assertEquals(0, reload(user).getNoShowStrikes());
    }

    @Test
    void strikesOnAnUnknownUserAre404() throws Exception {
        mockMvc.perform(withKey(post("/internal/users/" + Long.MAX_VALUE + "/strikes")))
                .andExpect(status().isNotFound());
        mockMvc.perform(withKey(delete("/internal/users/" + Long.MAX_VALUE + "/strikes")))
                .andExpect(status().isNotFound());
    }

    // admins

    @Test
    void adminsListsOnlyActiveAdmins() throws Exception {
        User activeAdmin = createUser(UserRole.ADMIN, UserStatus.ACTIVE, true);
        User inactiveAdmin = createUser(UserRole.ADMIN, UserStatus.INACTIVE, true);
        User client = createClient();

        // other admins may exist in the shared test database, so look for these by id
        mockMvc.perform(withKey(get("/internal/users/admins")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + activeAdmin.getId() + ")].email").value(hasItem(activeAdmin.getEmail())))
                .andExpect(jsonPath("$[?(@.id == " + activeAdmin.getId() + ")].displayName").value(hasItem("Internal Tester")))
                .andExpect(jsonPath("$[*].id").value(not(hasItem(inactiveAdmin.getId().intValue()))))
                .andExpect(jsonPath("$[*].id").value(not(hasItem(client.getId().intValue()))))
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    // advisors

    @Test
    void getAdvisorReturnsTheAdvisorAndItsUser() throws Exception {
        User user = createUser(UserRole.ADVISOR, UserStatus.ACTIVE, true);
        Advisor advisor = createAdvisorFor(user);

        mockMvc.perform(withKey(get("/internal/advisors/" + advisor.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(advisor.getId().intValue()))
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.userId").value(user.getId().intValue()))
                .andExpect(jsonPath("$.displayName").value("Internal Tester"))
                .andExpect(jsonPath("$.title").value("Planner"))
                .andExpect(jsonPath("$.specialty").value("GENERAL"));
    }

    @Test
    void getAdvisorByUserReturnsTheSameInfoPlusTheAdvisorId() throws Exception {
        User user = createUser(UserRole.ADVISOR, UserStatus.ACTIVE, true);
        Advisor advisor = createAdvisorFor(user);

        mockMvc.perform(withKey(get("/internal/advisors/by-user/" + user.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(advisor.getId().intValue()))
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.userId").value(user.getId().intValue()))
                .andExpect(jsonPath("$.displayName").value("Internal Tester"))
                .andExpect(jsonPath("$.title").value("Planner"))
                .andExpect(jsonPath("$.specialty").value("GENERAL"));
    }

    @Test
    void getAdvisorByUserIs404ForAUserWithoutAnAdvisorRowAndForAnUnknownUser() throws Exception {
        User client = createClient();

        mockMvc.perform(withKey(get("/internal/advisors/by-user/" + client.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Advisor not found"));
        mockMvc.perform(withKey(get("/internal/advisors/by-user/" + Long.MAX_VALUE)))
                .andExpect(status().isNotFound());
    }

    @Test
    void lookupMapsAdvisorIdsToInfoAndLeavesUnknownIdsOut() throws Exception {
        User firstUser = createUser(UserRole.ADVISOR, UserStatus.ACTIVE, true);
        Advisor first = createAdvisorFor(firstUser);
        User secondUser = createUser(UserRole.ADVISOR, UserStatus.INACTIVE, true);
        Advisor second = createAdvisorFor(secondUser);
        jdbcTemplate.update("UPDATE advisors SET active = false WHERE id = ?", second.getId());
        jdbcTemplate.update("UPDATE profiles SET first_name = 'Second' WHERE user_id = ?", secondUser.getId());

        mockMvc.perform(withKey(post("/internal/advisors/lookup"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[" + first.getId() + ", " + second.getId() + ", " + Long.MAX_VALUE + "]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$['" + first.getId() + "'].id").value(first.getId().intValue()))
                .andExpect(jsonPath("$['" + first.getId() + "'].userId").value(firstUser.getId().intValue()))
                .andExpect(jsonPath("$['" + first.getId() + "'].active").value(true))
                .andExpect(jsonPath("$['" + first.getId() + "'].displayName").value("Internal Tester"))
                .andExpect(jsonPath("$['" + first.getId() + "'].title").value("Planner"))
                .andExpect(jsonPath("$['" + first.getId() + "'].specialty").value("GENERAL"))
                .andExpect(jsonPath("$['" + second.getId() + "'].id").value(second.getId().intValue()))
                .andExpect(jsonPath("$['" + second.getId() + "'].active").value(false))
                .andExpect(jsonPath("$['" + second.getId() + "'].displayName").value("Second Tester"))
                .andExpect(jsonPath("$['" + Long.MAX_VALUE + "']").doesNotExist());
    }

    @Test
    void lookupWithAnEmptyListIsAnEmptyMapAndABadBodyIsA400() throws Exception {
        mockMvc.perform(withKey(post("/internal/advisors/lookup")).contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(withKey(post("/internal/advisors/lookup")).contentType(MediaType.APPLICATION_JSON).content("{\"ids\": [1]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theNewAdvisorEndpointsNeedTheKeyAndAUserTokenDoesNotHelp() throws Exception {
        User user = createUser(UserRole.ADVISOR, UserStatus.ACTIVE, true);
        Advisor advisor = createAdvisorFor(user);
        String bearer = "Bearer " + jwtUtils.generateToken(user);

        mockMvc.perform(get("/internal/advisors/by-user/" + user.getId())).andExpect(status().isForbidden());
        mockMvc.perform(get("/internal/advisors/by-user/" + user.getId()).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/internal/advisors/lookup").contentType(MediaType.APPLICATION_JSON).content("[" + advisor.getId() + "]")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/internal/advisors/lookup").contentType(MediaType.APPLICATION_JSON).content("[" + advisor.getId() + "]")
                        .header(KEY_HEADER, "not-the-key"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAdvisorShowsAnInactiveAdvisorAsInactive() throws Exception {
        User user = createUser(UserRole.ADVISOR, UserStatus.INACTIVE, true);
        Advisor advisor = createAdvisorFor(user);
        jdbcTemplate.update("UPDATE advisors SET active = false WHERE id = ?", advisor.getId());

        mockMvc.perform(withKey(get("/internal/advisors/" + advisor.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void getUnknownAdvisorIs404() throws Exception {
        mockMvc.perform(withKey(get("/internal/advisors/" + Long.MAX_VALUE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Advisor not found"));
    }

    // promote

    private String promoteBody(Long userId) {
        return "{\"userId\": " + userId + ", \"title\": \"Senior Planner\", \"specialty\": \"RETIREMENT_PLANNING\", \"yearsOfExperience\": 7}";
    }

    @Test
    void promoteMakesTheUserAnAdvisorAndCreatesTheActiveAdvisorRow() throws Exception {
        User client = createClient();

        mockMvc.perform(withKey(post("/internal/advisors/promote"))
                        .contentType(MediaType.APPLICATION_JSON).content(promoteBody(client.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(client.getId().intValue()))
                .andExpect(jsonPath("$.advisorId").isNumber());

        assertEquals(UserRole.ADVISOR, reload(client).getRole());
        Advisor advisor = advisorRepository.findByUser(client).orElseThrow();
        assertTrue(advisor.isActive());
        assertEquals("Senior Planner", advisor.getTitle());
        assertEquals(Specialty.RETIREMENT_PLANNING, advisor.getSpecialty());
        assertEquals(7, advisor.getYearsOfExperience());

        // and the lookup sees it
        mockMvc.perform(withKey(get("/internal/advisors/" + advisor.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(client.getId().intValue()));
    }

    @Test
    void promotingAnAdvisorAgainIsRejectedAndChangesNothing() throws Exception {
        User client = createClient();
        mockMvc.perform(withKey(post("/internal/advisors/promote"))
                        .contentType(MediaType.APPLICATION_JSON).content(promoteBody(client.getId())))
                .andExpect(status().isCreated());
        Long advisorId = advisorRepository.findByUser(client).orElseThrow().getId();

        mockMvc.perform(withKey(post("/internal/advisors/promote"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": " + client.getId() + ", \"title\": \"Other\", \"specialty\": \"GENERAL\", \"yearsOfExperience\": 1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This user is already an advisor"));

        Advisor advisor = advisorRepository.findByUser(client).orElseThrow();
        assertEquals(advisorId, advisor.getId());
        assertEquals("Senior Planner", advisor.getTitle());
    }

    @Test
    void anExistingAdvisorCreatedTheOtherWayIsAlsoRejected() throws Exception {
        User advisorUser = createUser(UserRole.ADVISOR, UserStatus.ACTIVE, true);
        createAdvisorFor(advisorUser);

        mockMvc.perform(withKey(post("/internal/advisors/promote"))
                        .contentType(MediaType.APPLICATION_JSON).content(promoteBody(advisorUser.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This user is already an advisor"));
    }

    @Test
    void anAdvisorRoleWithoutAnAdvisorRowIsRejectedAndNothingIsCreated() throws Exception {
        User advisorUser = createUser(UserRole.ADVISOR, UserStatus.ACTIVE, true);

        mockMvc.perform(withKey(post("/internal/advisors/promote"))
                        .contentType(MediaType.APPLICATION_JSON).content(promoteBody(advisorUser.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This user is already an advisor"));

        assertTrue(advisorRepository.findByUser(advisorUser).isEmpty());
        assertEquals(UserRole.ADVISOR, reload(advisorUser).getRole());
    }

    @Test
    void anAdminCannotBePromotedAndStaysAnAdmin() throws Exception {
        User admin = createUser(UserRole.ADMIN, UserStatus.ACTIVE, true);

        mockMvc.perform(withKey(post("/internal/advisors/promote"))
                        .contentType(MediaType.APPLICATION_JSON).content(promoteBody(admin.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only a client can be promoted to advisor"));

        assertEquals(UserRole.ADMIN, reload(admin).getRole());
        assertTrue(advisorRepository.findByUser(admin).isEmpty());
    }

    @Test
    void promoteRejectsAnUnknownUserAndBadInput() throws Exception {
        mockMvc.perform(withKey(post("/internal/advisors/promote"))
                        .contentType(MediaType.APPLICATION_JSON).content(promoteBody(Long.MAX_VALUE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));

        User client = createClient();
        for (String body : List.of(
                "{}",
                "{\"userId\": " + client.getId() + ", \"title\": \"\", \"specialty\": \"GENERAL\", \"yearsOfExperience\": 3}",
                "{\"userId\": " + client.getId() + ", \"title\": \"Planner\", \"yearsOfExperience\": 3}",
                "{\"userId\": " + client.getId() + ", \"title\": \"Planner\", \"specialty\": \"GENERAL\", \"yearsOfExperience\": -1}",
                "{\"userId\": " + client.getId() + ", \"title\": \"Planner\", \"specialty\": \"NOT_A_SPECIALTY\", \"yearsOfExperience\": 3}")) {
            mockMvc.perform(withKey(post("/internal/advisors/promote")).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }

        // nothing was promoted by the rejected calls
        assertEquals(UserRole.CLIENT, reload(client).getRole());
        assertTrue(advisorRepository.findByUser(client).isEmpty());
    }
}
