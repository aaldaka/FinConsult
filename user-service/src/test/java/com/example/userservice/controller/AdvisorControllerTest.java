package com.example.userservice.controller;

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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// runs on finconsult_test (TEST_DB_URL), which the main app's tests build. Every user and advisor here is created
// by the test with a unique email and deleted afterwards, real users and advisors are never touched
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class AdvisorControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired ProfileRepository profileRepository;
    @Autowired AdvisorRepository advisorRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JWTUtils jwtUtils;
    @Autowired JdbcTemplate jdbcTemplate;

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

    private User createUser(UserRole role) {
        User user = new User();
        user.setEmail("advisor-test-" + UUID.randomUUID() + "@test.com");
        createdEmails.add(user.getEmail());
        user.setPassword(passwordEncoder.encode("Str0ngP@ss!"));
        user.setRole(role);
        user.setUserStatus(UserStatus.ACTIVE);
        user = userRepository.save(user);

        Profile profile = new Profile();
        profile.setUser(user);
        profile.setFirstName("Adv");
        profile.setLastName("Tester");
        profile.setProfilePicturePath("/default.jpg");
        profileRepository.save(profile);
        return user;
    }

    private Advisor createAdvisor(User user, Specialty specialty, boolean active) {
        Advisor advisor = new Advisor();
        advisor.setUser(user);
        advisor.setTitle("Test Advisor");
        advisor.setSpecialty(specialty);
        advisor.setYearsOfExperience(7);
        advisor.setActive(active);
        return advisorRepository.save(advisor);
    }

    private String bearer(User user) {
        return "Bearer " + jwtUtils.generateToken(user);
    }

    // list and get

    @Test
    void theListHasTheActiveAdvisorsAndLeavesOutInactiveOnes() throws Exception {
        User caller = createUser(UserRole.CLIENT);
        Advisor active = createAdvisor(createUser(UserRole.ADVISOR), Specialty.INSURANCE, true);
        Advisor inactive = createAdvisor(createUser(UserRole.ADVISOR), Specialty.INSURANCE, false);

        mockMvc.perform(get("/advisors").header(HttpHeaders.AUTHORIZATION, bearer(caller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + active.getId() + ")]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.id == " + active.getId() + ")].name").value(hasItem("Adv Tester")))
                .andExpect(jsonPath("$[?(@.id == " + active.getId() + ")].title").value(hasItem("Test Advisor")))
                .andExpect(jsonPath("$[?(@.id == " + inactive.getId() + ")]").isEmpty());
    }

    @Test
    void theListCanBeFilteredBySpecialty() throws Exception {
        User caller = createUser(UserRole.CLIENT);
        Advisor banking = createAdvisor(createUser(UserRole.ADVISOR), Specialty.BUSINESS_BANKING, true);
        Advisor loans = createAdvisor(createUser(UserRole.ADVISOR), Specialty.LOANS, true);
        Advisor inactiveBanking = createAdvisor(createUser(UserRole.ADVISOR), Specialty.BUSINESS_BANKING, false);

        mockMvc.perform(get("/advisors").param("specialty", "BUSINESS_BANKING").header(HttpHeaders.AUTHORIZATION, bearer(caller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + banking.getId() + ")]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.id == " + loans.getId() + ")]").isEmpty())
                .andExpect(jsonPath("$[?(@.id == " + inactiveBanking.getId() + ")]").isEmpty())
                .andExpect(jsonPath("$[?(@.specialty != 'BUSINESS_BANKING')]").isEmpty());
    }

    @Test
    void anUnknownSpecialtyIsA400() throws Exception {
        User caller = createUser(UserRole.CLIENT);

        mockMvc.perform(get("/advisors").param("specialty", "NOPE").header(HttpHeaders.AUTHORIZATION, bearer(caller)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for specialty"));
    }

    @Test
    void getByIdReturnsTheAdvisorEvenWhenInactive() throws Exception {
        User caller = createUser(UserRole.CLIENT);
        Advisor advisor = createAdvisor(createUser(UserRole.ADVISOR), Specialty.MORTGAGES, false);

        mockMvc.perform(get("/advisors/{id}", advisor.getId()).header(HttpHeaders.AUTHORIZATION, bearer(caller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(advisor.getId()))
                .andExpect(jsonPath("$.name").value("Adv Tester"))
                .andExpect(jsonPath("$.title").value("Test Advisor"))
                .andExpect(jsonPath("$.specialty").value("MORTGAGES"))
                .andExpect(jsonPath("$.yearsOfExperience").value(7))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void getByUnknownIdIsA404() throws Exception {
        User caller = createUser(UserRole.CLIENT);

        mockMvc.perform(get("/advisors/{id}", Long.MAX_VALUE).header(HttpHeaders.AUTHORIZATION, bearer(caller)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Advisor not found"));
    }

    @Test
    void listingNeedsALogin() throws Exception {
        mockMvc.perform(get("/advisors")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/advisors/1")).andExpect(status().isUnauthorized());
    }

    // update my advisor profile

    @Test
    void anAdvisorCanUpdateTheirOwnDetails() throws Exception {
        User user = createUser(UserRole.ADVISOR);
        Advisor advisor = createAdvisor(user, Specialty.GENERAL, true);

        mockMvc.perform(patch("/advisors/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Senior Planner", "yearsOfExperience": 12, "specialty": "RETIREMENT_PLANNING"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(advisor.getId()))
                .andExpect(jsonPath("$.title").value("Senior Planner"))
                .andExpect(jsonPath("$.yearsOfExperience").value(12))
                .andExpect(jsonPath("$.specialty").value("RETIREMENT_PLANNING"));

        Advisor saved = advisorRepository.findById(advisor.getId()).orElseThrow();
        assertEquals("Senior Planner", saved.getTitle());
        assertEquals(12, saved.getYearsOfExperience());
        assertEquals(Specialty.RETIREMENT_PLANNING, saved.getSpecialty());
    }

    @Test
    void theUpdateChangesOnlyTheGivenFields() throws Exception {
        User user = createUser(UserRole.ADVISOR);
        Advisor advisor = createAdvisor(user, Specialty.LOANS, true);

        mockMvc.perform(patch("/advisors/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Only The Title\"}"))
                .andExpect(status().isOk());

        Advisor saved = advisorRepository.findById(advisor.getId()).orElseThrow();
        assertEquals("Only The Title", saved.getTitle());
        assertEquals(7, saved.getYearsOfExperience());
        assertEquals(Specialty.LOANS, saved.getSpecialty());
    }

    @Test
    void theUpdateRejectsValuesOutOfRange() throws Exception {
        User user = createUser(UserRole.ADVISOR);
        createAdvisor(user, Specialty.LOANS, true);

        mockMvc.perform(patch("/advisors/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"yearsOfExperience\": 51}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(startsWith("yearsOfExperience: ")));

        mockMvc.perform(patch("/advisors/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"" + "a".repeat(51) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(startsWith("title: ")));
    }

    @Test
    void anAdvisorWithoutAnAdvisorRowGetsA404() throws Exception {
        User user = createUser(UserRole.ADVISOR);

        mockMvc.perform(patch("/advisors/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"x\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Advisor profile was not found"));
    }

    @Test
    void aClientCannotUpdateAnAdvisorProfile() throws Exception {
        User client = createUser(UserRole.CLIENT);

        mockMvc.perform(patch("/advisors/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"x\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("You do not have permission to access this resource"));
    }

    @Test
    void updatingNeedsALogin() throws Exception {
        mockMvc.perform(patch("/advisors/me").contentType(MediaType.APPLICATION_JSON).content("{\"title\": \"x\"}"))
                .andExpect(status().isUnauthorized());
    }
}
