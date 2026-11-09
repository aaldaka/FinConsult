package com.example.notificationservice.service;

import com.example.notificationservice.exception.InformationNotFound;
import com.example.notificationservice.exception.InvalidRequest;
import com.example.notificationservice.model.Notification;
import com.example.notificationservice.model.enums.NotificationType;
import com.example.notificationservice.model.response.NotificationResponse;
import com.example.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class NotificationServiceTest {

    private static final long USER = 1L;
    private static final long OTHER_USER = 2L;

    @Autowired NotificationService notificationService;
    @Autowired NotificationRepository notificationRepository;

    @BeforeEach
    void emptyTable() {
        notificationRepository.deleteAll();
    }

    @Test
    void createThenListReturnsTheNotification() {
        Notification created = notificationService.create(USER, NotificationType.APPOINTMENT_BOOKED, "Maryam booked a slot", 5L);

        List<NotificationResponse> mine = notificationService.getMyNotifications(USER);

        assertEquals(1, mine.size());
        NotificationResponse found = mine.get(0);
        assertEquals(created.getId(), found.getId());
        assertEquals(NotificationType.APPOINTMENT_BOOKED, found.getType());
        assertEquals("Maryam booked a slot", found.getMessage());
        assertEquals(5L, found.getRelatedAppointmentId());
        assertFalse(found.isRead());
        assertNotNull(found.getCreatedAt());
        assertTrue(notificationService.getMyNotifications(OTHER_USER).isEmpty());
    }

    @Test
    void notificationWithoutAnAppointmentKeepsTheIdNull() {
        notificationService.create(USER, NotificationType.KYC_REVIEWED, "Your NATIONAL_ID document has been approved.", null);

        assertNull(notificationService.getMyNotifications(USER).get(0).getRelatedAppointmentId());
    }

    @Test
    void unreadListLeavesOutReadNotifications() {
        Notification first = notificationService.create(USER, NotificationType.APPOINTMENT_BOOKED, "first", 1L);
        Notification second = notificationService.create(USER, NotificationType.APPOINTMENT_CONFIRMED, "second", 1L);
        notificationService.create(OTHER_USER, NotificationType.APPOINTMENT_BOOKED, "someone else's", 2L);

        notificationService.markAsRead(first.getId(), USER);

        List<NotificationResponse> unread = notificationService.getMyUnreadNotifications(USER);
        assertEquals(1, unread.size());
        assertEquals(second.getId(), unread.get(0).getId());
        assertEquals(2, notificationService.getMyNotifications(USER).size());
    }

    @Test
    void markAsReadSetsTheFlagAndReturnsTheUpdatedNotification() {
        Notification created = notificationService.create(USER, NotificationType.APPOINTMENT_CANCELLED, "cancelled", 3L);

        NotificationResponse updated = notificationService.markAsRead(created.getId(), USER);

        assertTrue(updated.isRead());
        assertTrue(notificationRepository.findById(created.getId()).orElseThrow().isRead());
    }

    @Test
    void markingAnotherUsersNotificationFailsAndChangesNothing() {
        Notification created = notificationService.create(USER, NotificationType.APPOINTMENT_BOOKED, "mine", 4L);

        InvalidRequest error = assertThrows(InvalidRequest.class,
                () -> notificationService.markAsRead(created.getId(), OTHER_USER));

        assertEquals("This is not your notification", error.getMessage());
        assertFalse(notificationRepository.findById(created.getId()).orElseThrow().isRead());
    }

    @Test
    void markingAnUnknownNotificationFails() {
        InformationNotFound error = assertThrows(InformationNotFound.class,
                () -> notificationService.markAsRead(999_999L, USER));

        assertEquals("Notification not found", error.getMessage());
    }

    @Test
    void markAllReturnsHowManyWereUnreadAndLeavesOtherUsersAlone() {
        Notification alreadyRead = notificationService.create(USER, NotificationType.APPOINTMENT_BOOKED, "a", 1L);
        notificationService.create(USER, NotificationType.APPOINTMENT_CONFIRMED, "b", 1L);
        notificationService.create(USER, NotificationType.APPOINTMENT_NO_SHOW, "c", 1L);
        notificationService.create(OTHER_USER, NotificationType.APPOINTMENT_BOOKED, "d", 2L);
        notificationService.markAsRead(alreadyRead.getId(), USER);

        int updated = notificationService.markAllAsRead(USER);

        assertEquals(2, updated);
        assertTrue(notificationService.getMyUnreadNotifications(USER).isEmpty());
        assertEquals(1, notificationService.getMyUnreadNotifications(OTHER_USER).size());
        assertEquals(0, notificationService.markAllAsRead(USER));
    }
}
