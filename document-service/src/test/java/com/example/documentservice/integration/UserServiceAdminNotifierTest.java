package com.example.documentservice.integration;

import com.example.documentservice.client.AdminInfo;
import com.example.documentservice.client.UserClient;
import com.example.documentservice.exception.ServiceUnavailable;
import com.example.documentservice.model.enums.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// no Spring and no database, the user service and the notification stub are mocks
@ExtendWith(MockitoExtension.class)
class UserServiceAdminNotifierTest {

    @Mock UserClient userClient;
    @Mock NotificationPublisher notificationPublisher;
    @InjectMocks UserServiceAdminNotifier notifier;

    @Test
    void everyAdminGetsTheNotification() {
        when(userClient.admins()).thenReturn(List.of(new AdminInfo(1L, "a@test.com", "A"), new AdminInfo(2L, "b@test.com", "B")));

        notifier.notifyAdmins(NotificationType.DOCUMENT_FLAGGED, "2 KYC document(s) from Reem Ali were flagged");

        verify(notificationPublisher).publish(1L, NotificationType.DOCUMENT_FLAGGED, "2 KYC document(s) from Reem Ali were flagged");
        verify(notificationPublisher).publish(2L, NotificationType.DOCUMENT_FLAGGED, "2 KYC document(s) from Reem Ali were flagged");
    }

    @Test
    void aFailedAdminLookupIsLoggedAndNeverThrown() {
        when(userClient.admins()).thenThrow(new ServiceUnavailable("down"));

        assertDoesNotThrow(() -> notifier.notifyAdmins(NotificationType.DOCUMENT_FLAGGED, "flagged"));

        verify(notificationPublisher, never()).publish(anyLong(), any(), anyString());
    }

    @Test
    void oneFailedNotificationDoesNotStopTheOthers() {
        when(userClient.admins()).thenReturn(List.of(new AdminInfo(1L, "a@test.com", "A"), new AdminInfo(2L, "b@test.com", "B")));
        doThrow(new RuntimeException("boom")).when(notificationPublisher).publish(1L, NotificationType.DOCUMENT_FLAGGED, "flagged");

        assertDoesNotThrow(() -> notifier.notifyAdmins(NotificationType.DOCUMENT_FLAGGED, "flagged"));

        verify(notificationPublisher).publish(2L, NotificationType.DOCUMENT_FLAGGED, "flagged");
    }
}
