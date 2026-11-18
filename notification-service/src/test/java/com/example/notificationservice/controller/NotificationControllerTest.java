package com.example.notificationservice.controller;

import com.example.notificationservice.model.Notification;
import com.example.notificationservice.model.enums.NotificationType;
import com.example.notificationservice.service.NotificationService;
import com.example.notificationservice.support.TestTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// runs on finconsult_test (TEST_DB_URL), which docker/db/init/00-create-test-db.sh builds, with real users from its users table
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class NotificationControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired NotificationService notificationService;
    @Autowired JdbcTemplate jdbcTemplate;

    // the same secret the app validates with, so the tokens built here are accepted
    @Value("${jwt.secret}") String jwtSecret;

    private long userA;
    private long userB;
    private final List<Long> createdIds = new ArrayList<>();

    // users that have no notifications yet, so the lists in the tests contain only what the test created
    @BeforeEach
    void findUsers() {
        List<Long> users = jdbcTemplate.queryForList(
                "SELECT u.id FROM users u WHERE NOT EXISTS (SELECT 1 FROM notifications n WHERE n.user_id = u.id) ORDER BY u.id LIMIT 2",
                Long.class);
        assertEquals(2, users.size(), "Need 2 users without notifications in finconsult_test. "
                + "Run the main app's tests first (./mvnw test in the project root), they create the users.");
        userA = users.get(0);
        userB = users.get(1);
    }

    // delete only what this test created, never the whole table
    @AfterEach
    void deleteWhatThisTestCreated() {
        createdIds.forEach(id -> jdbcTemplate.update("DELETE FROM notifications WHERE id = ?", id));
    }

    private Notification create(long userId, NotificationType type, String message, Long relatedAppointmentId) {
        Notification created = notificationService.create(userId, type, message, relatedAppointmentId);
        createdIds.add(created.getId());
        return created;
    }

    private long anExistingAppointmentId() {
        List<Long> ids = jdbcTemplate.queryForList("SELECT id FROM appointments ORDER BY id LIMIT 1", Long.class);
        assertFalse(ids.isEmpty(), "Need an appointment in finconsult_test. "
                + "Run the main app's tests first (./mvnw test in the project root).");
        return ids.get(0);
    }

    private String bearer(long userId) {
        return "Bearer " + TestTokens.valid(jwtSecret, userId, "CLIENT");
    }

    // list

    @Test
    void listMineReturnsOnlyMyNotifications() throws Exception {
        create(userA, NotificationType.APPOINTMENT_BOOKED, "first", null);
        create(userA, NotificationType.KYC_REVIEWED, "second", null);
        create(userB, NotificationType.APPOINTMENT_BOOKED, "not mine", null);

        mockMvc.perform(get("/notifications/mine").header(HttpHeaders.AUTHORIZATION, bearer(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void listMineHasTheSameFieldsAsTheMainApp() throws Exception {
        long appointmentId = anExistingAppointmentId();
        Notification created = create(userA, NotificationType.APPOINTMENT_BOOKED, "Maryam booked a slot", appointmentId);

        mockMvc.perform(get("/notifications/mine").header(HttpHeaders.AUTHORIZATION, bearer(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(created.getId()))
                .andExpect(jsonPath("$[0].type").value("APPOINTMENT_BOOKED"))
                .andExpect(jsonPath("$[0].message").value("Maryam booked a slot"))
                .andExpect(jsonPath("$[0].read").value(false))
                .andExpect(jsonPath("$[0].relatedAppointmentId").value(appointmentId))
                .andExpect(jsonPath("$[0].createdAt").exists());
    }

    @Test
    void listUnreadLeavesOutReadOnes() throws Exception {
        Notification read = create(userA, NotificationType.APPOINTMENT_BOOKED, "already read", null);
        create(userA, NotificationType.APPOINTMENT_CONFIRMED, "still unread", null);
        notificationService.markAsRead(read.getId(), userA);

        mockMvc.perform(get("/notifications/mine/unread").header(HttpHeaders.AUTHORIZATION, bearer(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].message").value("still unread"));
    }

    // mark as read

    @Test
    void markAsReadReturnsTheUpdatedNotification() throws Exception {
        Notification created = create(userA, NotificationType.APPOINTMENT_CANCELLED, "cancelled", null);

        mockMvc.perform(patch("/notifications/{id}/read", created.getId()).header(HttpHeaders.AUTHORIZATION, bearer(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.getId()))
                .andExpect(jsonPath("$.read").value(true));
    }

    @Test
    void readingAnotherUsersNotificationIsA400WithTheSameErrorShape() throws Exception {
        Notification created = create(userA, NotificationType.APPOINTMENT_BOOKED, "mine", null);

        mockMvc.perform(patch("/notifications/{id}/read", created.getId()).header(HttpHeaders.AUTHORIZATION, bearer(userB)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("This is not your notification"))
                .andExpect(jsonPath("$.path").value("/notifications/" + created.getId() + "/read"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void unknownIdIsA404() throws Exception {
        mockMvc.perform(patch("/notifications/{id}/read", Long.MAX_VALUE).header(HttpHeaders.AUTHORIZATION, bearer(userA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Notification not found"));
    }

    @Test
    void anIdThatIsNotANumberIsA400() throws Exception {
        mockMvc.perform(patch("/notifications/{id}/read", "abc").header(HttpHeaders.AUTHORIZATION, bearer(userA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for id"));
    }

    // mark all

    @Test
    void markAllReturnsTheCountInTheSameShapeAsTheMainApp() throws Exception {
        Notification alreadyRead = create(userA, NotificationType.APPOINTMENT_BOOKED, "a", null);
        create(userA, NotificationType.APPOINTMENT_CONFIRMED, "b", null);
        create(userA, NotificationType.APPOINTMENT_NO_SHOW, "c", null);
        create(userB, NotificationType.APPOINTMENT_BOOKED, "d", null);
        notificationService.markAsRead(alreadyRead.getId(), userA);

        mockMvc.perform(patch("/notifications/mine/read-all").header(HttpHeaders.AUTHORIZATION, bearer(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("2 notifications marked as read"));

        mockMvc.perform(get("/notifications/mine/unread").header(HttpHeaders.AUTHORIZATION, bearer(userB)))
                .andExpect(jsonPath("$.length()").value(1));
    }

    // authentication

    @Test
    void noTokenIsA401() throws Exception {
        mockMvc.perform(get("/notifications/mine"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenWithTheWrongSignatureIsA401() throws Exception {
        String forged = "Bearer " + TestTokens.valid(TestTokens.OTHER_SECRET, userA, "CLIENT");

        mockMvc.perform(get("/notifications/mine").header(HttpHeaders.AUTHORIZATION, forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anExpiredTokenIsA401() throws Exception {
        String expired = "Bearer " + TestTokens.expired(jwtSecret, userA, "CLIENT");

        mockMvc.perform(get("/notifications/mine").header(HttpHeaders.AUTHORIZATION, expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenWithoutAUserIdIsA401() throws Exception {
        String old = "Bearer " + TestTokens.withoutUserId(jwtSecret, "CLIENT");

        mockMvc.perform(get("/notifications/mine").header(HttpHeaders.AUTHORIZATION, old))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthIsOpenWithoutAToken() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
