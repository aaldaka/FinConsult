package com.example.notificationservice.controller;

import com.example.notificationservice.model.Notification;
import com.example.notificationservice.model.enums.NotificationType;
import com.example.notificationservice.repository.NotificationRepository;
import com.example.notificationservice.service.NotificationService;
import com.example.notificationservice.support.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class NotificationControllerTest {

    private static final long USER = 1L;
    private static final long OTHER_USER = 2L;

    @Autowired MockMvc mockMvc;
    @Autowired NotificationService notificationService;
    @Autowired NotificationRepository notificationRepository;

    // the same secret the app validates with, so the tokens built here are accepted
    @Value("${jwt.secret}") String jwtSecret;

    @BeforeEach
    void emptyTable() {
        notificationRepository.deleteAll();
    }

    private String bearer(long userId) {
        return "Bearer " + TestTokens.valid(jwtSecret, userId, "CLIENT");
    }

    // list

    @Test
    void listMineReturnsOnlyMyNotifications() throws Exception {
        notificationService.create(USER, NotificationType.APPOINTMENT_BOOKED, "first", 5L);
        notificationService.create(USER, NotificationType.KYC_REVIEWED, "second", null);
        notificationService.create(OTHER_USER, NotificationType.APPOINTMENT_BOOKED, "not mine", 6L);

        mockMvc.perform(get("/notifications/mine").header(HttpHeaders.AUTHORIZATION, bearer(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void listMineHasTheSameFieldsAsTheMainApp() throws Exception {
        Notification created = notificationService.create(USER, NotificationType.APPOINTMENT_BOOKED, "Maryam booked a slot", 5L);

        mockMvc.perform(get("/notifications/mine").header(HttpHeaders.AUTHORIZATION, bearer(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(created.getId()))
                .andExpect(jsonPath("$[0].type").value("APPOINTMENT_BOOKED"))
                .andExpect(jsonPath("$[0].message").value("Maryam booked a slot"))
                .andExpect(jsonPath("$[0].read").value(false))
                .andExpect(jsonPath("$[0].relatedAppointmentId").value(5))
                .andExpect(jsonPath("$[0].createdAt").exists());
    }

    @Test
    void listUnreadLeavesOutReadOnes() throws Exception {
        Notification read = notificationService.create(USER, NotificationType.APPOINTMENT_BOOKED, "already read", 1L);
        notificationService.create(USER, NotificationType.APPOINTMENT_CONFIRMED, "still unread", 1L);
        notificationService.markAsRead(read.getId(), USER);

        mockMvc.perform(get("/notifications/mine/unread").header(HttpHeaders.AUTHORIZATION, bearer(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].message").value("still unread"));
    }

    // mark as read

    @Test
    void markAsReadReturnsTheUpdatedNotification() throws Exception {
        Notification created = notificationService.create(USER, NotificationType.APPOINTMENT_CANCELLED, "cancelled", 3L);

        mockMvc.perform(patch("/notifications/{id}/read", created.getId()).header(HttpHeaders.AUTHORIZATION, bearer(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.getId()))
                .andExpect(jsonPath("$.read").value(true));
    }

    @Test
    void readingAnotherUsersNotificationIsA400WithTheSameErrorShape() throws Exception {
        Notification created = notificationService.create(USER, NotificationType.APPOINTMENT_BOOKED, "mine", 4L);

        mockMvc.perform(patch("/notifications/{id}/read", created.getId()).header(HttpHeaders.AUTHORIZATION, bearer(OTHER_USER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("This is not your notification"))
                .andExpect(jsonPath("$.path").value("/notifications/" + created.getId() + "/read"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void unknownIdIsA404() throws Exception {
        mockMvc.perform(patch("/notifications/{id}/read", 999_999L).header(HttpHeaders.AUTHORIZATION, bearer(USER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Notification not found"));
    }

    @Test
    void anIdThatIsNotANumberIsA400() throws Exception {
        mockMvc.perform(patch("/notifications/{id}/read", "abc").header(HttpHeaders.AUTHORIZATION, bearer(USER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for id"));
    }

    // mark all

    @Test
    void markAllReturnsTheCountInTheSameShapeAsTheMainApp() throws Exception {
        Notification alreadyRead = notificationService.create(USER, NotificationType.APPOINTMENT_BOOKED, "a", 1L);
        notificationService.create(USER, NotificationType.APPOINTMENT_CONFIRMED, "b", 1L);
        notificationService.create(USER, NotificationType.APPOINTMENT_NO_SHOW, "c", 1L);
        notificationService.create(OTHER_USER, NotificationType.APPOINTMENT_BOOKED, "d", 2L);
        notificationService.markAsRead(alreadyRead.getId(), USER);

        mockMvc.perform(patch("/notifications/mine/read-all").header(HttpHeaders.AUTHORIZATION, bearer(USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("2 notifications marked as read"));

        mockMvc.perform(get("/notifications/mine/unread").header(HttpHeaders.AUTHORIZATION, bearer(OTHER_USER)))
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
        String forged = "Bearer " + TestTokens.valid(TestTokens.OTHER_SECRET, USER, "CLIENT");

        mockMvc.perform(get("/notifications/mine").header(HttpHeaders.AUTHORIZATION, forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anExpiredTokenIsA401() throws Exception {
        String expired = "Bearer " + TestTokens.expired(jwtSecret, USER, "CLIENT");

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
