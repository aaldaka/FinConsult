package com.example.notificationservice.service;

import com.example.notificationservice.exception.InformationNotFound;
import com.example.notificationservice.exception.InvalidRequest;
import com.example.notificationservice.model.Notification;
import com.example.notificationservice.model.enums.NotificationType;
import com.example.notificationservice.model.response.NotificationResponse;
import com.example.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// runs on finconsult_test (TEST_DB_URL), which docker/db/init/00-create-test-db.sh builds, with real users from its users table
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class NotificationServiceTest {

    @Autowired NotificationService notificationService;
    @Autowired NotificationRepository notificationRepository;
    @Autowired JdbcTemplate jdbcTemplate;

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

    @Test
    void createThenListReturnsTheNotification() {
        long appointmentId = anExistingAppointmentId();
        Notification created = create(userA, NotificationType.APPOINTMENT_BOOKED, "Maryam booked a slot", appointmentId);

        List<NotificationResponse> mine = notificationService.getMyNotifications(userA);

        assertEquals(1, mine.size());
        NotificationResponse found = mine.get(0);
        assertEquals(created.getId(), found.getId());
        assertEquals(NotificationType.APPOINTMENT_BOOKED, found.getType());
        assertEquals("Maryam booked a slot", found.getMessage());
        assertEquals(appointmentId, found.getRelatedAppointmentId());
        assertFalse(found.isRead());
        assertNotNull(found.getCreatedAt());
        assertTrue(notificationService.getMyNotifications(userB).isEmpty());
    }

    @Test
    void notificationWithoutAnAppointmentKeepsTheIdNull() {
        create(userA, NotificationType.KYC_REVIEWED, "Your NATIONAL_ID document has been approved.", null);

        assertNull(notificationService.getMyNotifications(userA).get(0).getRelatedAppointmentId());
    }

    @Test
    void unreadListLeavesOutReadNotifications() {
        Notification first = create(userA, NotificationType.APPOINTMENT_BOOKED, "first", null);
        Notification second = create(userA, NotificationType.APPOINTMENT_CONFIRMED, "second", null);
        create(userB, NotificationType.APPOINTMENT_BOOKED, "someone else's", null);

        notificationService.markAsRead(first.getId(), userA);

        List<NotificationResponse> unread = notificationService.getMyUnreadNotifications(userA);
        assertEquals(1, unread.size());
        assertEquals(second.getId(), unread.get(0).getId());
        assertEquals(2, notificationService.getMyNotifications(userA).size());
    }

    @Test
    void markAsReadSetsTheFlagAndReturnsTheUpdatedNotification() {
        Notification created = create(userA, NotificationType.APPOINTMENT_CANCELLED, "cancelled", null);

        NotificationResponse updated = notificationService.markAsRead(created.getId(), userA);

        assertTrue(updated.isRead());
        assertTrue(notificationRepository.findById(created.getId()).orElseThrow().isRead());
    }

    @Test
    void markingAnotherUsersNotificationFailsAndChangesNothing() {
        Notification created = create(userA, NotificationType.APPOINTMENT_BOOKED, "mine", null);

        InvalidRequest error = assertThrows(InvalidRequest.class,
                () -> notificationService.markAsRead(created.getId(), userB));

        assertEquals("This is not your notification", error.getMessage());
        assertFalse(notificationRepository.findById(created.getId()).orElseThrow().isRead());
    }

    @Test
    void markingAnUnknownNotificationFails() {
        InformationNotFound error = assertThrows(InformationNotFound.class,
                () -> notificationService.markAsRead(Long.MAX_VALUE, userA));

        assertEquals("Notification not found", error.getMessage());
    }

    @Test
    void markAllReturnsHowManyWereUnreadAndLeavesOtherUsersAlone() {
        Notification alreadyRead = create(userA, NotificationType.APPOINTMENT_BOOKED, "a", null);
        create(userA, NotificationType.APPOINTMENT_CONFIRMED, "b", null);
        create(userA, NotificationType.APPOINTMENT_NO_SHOW, "c", null);
        create(userB, NotificationType.APPOINTMENT_BOOKED, "d", null);
        notificationService.markAsRead(alreadyRead.getId(), userA);

        int updated = notificationService.markAllAsRead(userA);

        assertEquals(2, updated);
        assertTrue(notificationService.getMyUnreadNotifications(userA).isEmpty());
        assertEquals(1, notificationService.getMyUnreadNotifications(userB).size());
        assertEquals(0, notificationService.markAllAsRead(userA));
    }
}
