package com.example.userservice.controller;

import com.example.userservice.integration.AuditRecorder;
import com.example.userservice.model.Profile;
import com.example.userservice.model.User;
import com.example.userservice.model.enums.AuditAction;
import com.example.userservice.model.enums.UserRole;
import com.example.userservice.model.enums.UserStatus;
import com.example.userservice.repository.ProfileRepository;
import com.example.userservice.repository.UserRepository;
import com.example.userservice.security.JWTUtils;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// runs on finconsult_test (TEST_DB_URL), which the main app's tests build. Every user here is created by the
// test with a unique email and deleted afterwards, real users are never touched. Pictures go into a temp directory
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class ProfileControllerTest {

    @TempDir static Path uploadDir;

    @DynamicPropertySource
    static void uploadDirectory(DynamicPropertyRegistry registry) {
        registry.add("app.upload-dir", () -> uploadDir.toString());
    }

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired ProfileRepository profileRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JWTUtils jwtUtils;
    @Autowired JdbcTemplate jdbcTemplate;

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

    private User createUser(boolean withProfile) {
        User user = new User();
        user.setEmail("profile-test-" + UUID.randomUUID() + "@test.com");
        createdEmails.add(user.getEmail());
        user.setPassword(passwordEncoder.encode("Str0ngP@ss!"));
        user.setRole(UserRole.CLIENT);
        user.setUserStatus(UserStatus.ACTIVE);
        user = userRepository.save(user);

        if (withProfile) {
            Profile profile = new Profile();
            profile.setUser(user);
            profile.setFirstName("Test");
            profile.setLastName("User");
            profile.setProfilePicturePath("/default.jpg");
            profileRepository.save(profile);
        }
        return user;
    }

    private String bearer(User user) {
        return "Bearer " + jwtUtils.generateToken(user);
    }

    private Profile profileOf(User user) {
        return profileRepository.findByUser(user).orElseThrow();
    }

    // get and update

    @Test
    void getMyProfileReturnsTheCallersProfile() throws Exception {
        User user = createUser(true);

        mockMvc.perform(get("/profile/me").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Test"))
                .andExpect(jsonPath("$.lastName").value("User"))
                .andExpect(jsonPath("$.profilePicturePath").value("/default.jpg"))
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.dateOfBirth").doesNotExist());
    }

    @Test
    void aUserWithoutAProfileGetsA404() throws Exception {
        User user = createUser(false);

        mockMvc.perform(get("/profile/me").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Profile not found"));
    }

    @Test
    void profileNeedsALogin() throws Exception {
        mockMvc.perform(get("/profile/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/profile/me").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateChangesOnlyTheGivenFieldsAndRecordsAnAuditEntry() throws Exception {
        User user = createUser(true);

        mockMvc.perform(patch("/profile/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName": "Jane", "phone": "+97333000001", "dateOfBirth": "1995-04-12"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Jane"))
                .andExpect(jsonPath("$.lastName").value("User"))
                .andExpect(jsonPath("$.phone").value("+97333000001"))
                .andExpect(jsonPath("$.dateOfBirth").value("1995-04-12"));

        Profile saved = profileOf(user);
        assertEquals("Jane", saved.getFirstName());
        assertEquals("User", saved.getLastName());
        assertEquals("+97333000001", saved.getPhone());
        verify(auditRecorder).record(eq(user.getId()), eq(AuditAction.PROFILE_UPDATED), eq("Profile"),
                eq(saved.getId()), eq("Profile updated"));
    }

    @Test
    void updateRejectsValuesThatAreTooLong() throws Exception {
        User user = createUser(true);

        mockMvc.perform(patch("/profile/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\": \"" + "a".repeat(51) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(startsWith("firstName: ")));

        assertEquals("Test", profileOf(user).getFirstName());
        verifyNoInteractions(auditRecorder);
    }

    // picture

    @Test
    void uploadStoresThePictureAndServesItPublicly() throws Exception {
        User user = createUser(true);
        byte[] bytes = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
        MockMultipartFile file = new MockMultipartFile("file", "me.png", "image/png", bytes);

        String body = mockMvc.perform(multipart("/profile/me/picture").file(file).header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profilePicturePath").value(org.hamcrest.Matchers.matchesPattern("/uploads/profile_pictures/[0-9a-f-]{36}\\.png")))
                .andReturn().getResponse().getContentAsString();

        String path = JsonPath.read(body, "$.profilePicturePath");
        String filename = path.substring(path.lastIndexOf('/') + 1);
        assertTrue(Files.exists(uploadDir.resolve("profile_pictures").resolve(filename)), "the file is in the temp directory");
        Profile saved = profileOf(user);
        assertEquals(path, saved.getProfilePicturePath());
        verify(auditRecorder).record(eq(user.getId()), eq(AuditAction.PROFILE_PICTURE_UPDATED), eq("Profile"),
                eq(saved.getId()), eq("Profile picture changed"));

        // pictures are public, no token needed
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(content().bytes(bytes));
    }

    @Test
    void uploadRefusesAWrongTypeAnEmptyFileAndATooBigFile() throws Exception {
        User user = createUser(true);

        mockMvc.perform(multipart("/profile/me/picture")
                        .file(new MockMultipartFile("file", "notes.txt", "text/plain", new byte[]{1}))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("File type/extension is not allowed"));

        mockMvc.perform(multipart("/profile/me/picture")
                        .file(new MockMultipartFile("file", "me.png", "image/png", new byte[0]))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No file was uploaded"));

        mockMvc.perform(multipart("/profile/me/picture")
                        .file(new MockMultipartFile("file", "big.png", "image/png", new byte[5 * 1024 * 1024 + 1]))
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("File size cannot exceed 5 Mbs"));

        assertEquals("/default.jpg", profileOf(user).getProfilePicturePath());
        verifyNoInteractions(auditRecorder);
    }

    @Test
    void uploadWithoutAFilePartIsA400() throws Exception {
        User user = createUser(true);

        mockMvc.perform(multipart("/profile/me/picture").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing part file"));
    }

    @Test
    void uploadNeedsALogin() throws Exception {
        mockMvc.perform(multipart("/profile/me/picture")
                        .file(new MockMultipartFile("file", "me.png", "image/png", new byte[]{1})))
                .andExpect(status().isUnauthorized());
    }
}
