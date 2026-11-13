package com.example.userservice.controller;

import com.example.userservice.integration.AppointmentCancellation;
import com.example.userservice.integration.AuditRecorder;
import com.example.userservice.integration.RestAppointmentCancellation;
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
import com.example.userservice.service.EmailService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// runs on finconsult_test (TEST_DB_URL). Unlike AdminControllerTest the appointment cancellation is the real
// RestAppointmentCancellation bean, pointed at a port where nothing listens, so booking is "down". The deactivations
// must still answer as before and stay saved. Every user here is created by the test and deleted afterwards
@SpringBootTest(properties = "booking-service.url=http://127.0.0.1:1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class DeactivationWithBookingDownTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired ProfileRepository profileRepository;
    @Autowired AdvisorRepository advisorRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JWTUtils jwtUtils;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired AppointmentCancellation appointmentCancellation;

    @MockitoBean EmailService emailService;
    @MockitoBean AuditRecorder auditRecorder;

    private final List<String> createdEmails = new ArrayList<>();

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

    private User createUser(UserRole role) {
        String email = "booking-down-" + UUID.randomUUID() + "@test.com";
        createdEmails.add(email);
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("Str0ngP@ss!"));
        user.setRole(role);
        user.setUserStatus(UserStatus.ACTIVE);
        user = userRepository.save(user);

        Profile profile = new Profile();
        profile.setUser(user);
        profile.setFirstName("Test");
        profile.setLastName("Person");
        profile.setProfilePicturePath("/default.jpg");
        profileRepository.save(profile);
        return user;
    }

    private String bearer(User user) {
        return "Bearer " + jwtUtils.generateToken(user);
    }

    @Test
    void theActiveBeanIsTheRestOne() {
        assertInstanceOf(RestAppointmentCancellation.class, appointmentCancellation);
    }

    @Test
    void deactivatingAClientStillWorksAndStaysSavedWhenBookingIsDown() throws Exception {
        User admin = createUser(UserRole.ADMIN);
        User client = createUser(UserRole.CLIENT);

        mockMvc.perform(patch("/admin/users/{id}/deactivate", client.getId()).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("User deactivated"));

        assertEquals(UserStatus.INACTIVE, userRepository.findById(client.getId()).orElseThrow().getUserStatus());
    }

    @Test
    void deactivatingAnAdvisorStillWorksAndStaysSavedWhenBookingIsDown() throws Exception {
        User admin = createUser(UserRole.ADMIN);
        User advisorUser = createUser(UserRole.ADVISOR);
        Advisor advisor = new Advisor();
        advisor.setUser(advisorUser);
        advisor.setTitle("Test Advisor");
        advisor.setSpecialty(Specialty.INSURANCE);
        advisor.setYearsOfExperience(4);
        advisor.setActive(true);
        advisor = advisorRepository.save(advisor);

        mockMvc.perform(patch("/admin/advisors/{id}/deactivate", advisor.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"left the company\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("Advisor deactivated"));

        assertFalse(advisorRepository.findById(advisor.getId()).orElseThrow().isActive());
        assertEquals(UserStatus.INACTIVE, userRepository.findById(advisorUser.getId()).orElseThrow().getUserStatus());
    }
}
