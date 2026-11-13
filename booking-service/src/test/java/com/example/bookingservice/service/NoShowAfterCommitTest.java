package com.example.bookingservice.service;

import com.example.bookingservice.client.AdvisorClient;
import com.example.bookingservice.client.AdvisorInfo;
import com.example.bookingservice.client.StrikeClient;
import com.example.bookingservice.client.StrikeResult;
import com.example.bookingservice.client.UserClient;
import com.example.bookingservice.exception.ServiceUnavailable;
import com.example.bookingservice.integration.AuditRecorder;
import com.example.bookingservice.integration.KycCheck;
import com.example.bookingservice.integration.NotificationPublisher;
import com.example.bookingservice.model.AvailabilitySlot;
import com.example.bookingservice.model.enums.AuditAction;
import com.example.bookingservice.model.enums.NotificationType;
import com.example.bookingservice.model.enums.SlotStatus;
import com.example.bookingservice.model.enums.Specialty;
import com.example.bookingservice.repository.AvailabilitySlotRepository;
import com.example.bookingservice.support.AppointmentTestData;
import com.example.bookingservice.support.BookingLookups;
import com.example.bookingservice.support.SlotTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// runs on finconsult_test (TEST_DB_URL), no @Transactional on the class, so every save is a real commit. The appointment
// is booked on a slot that started 30 minutes ago and ends in 90, so the no-show is allowed and the completion job
// leaves it alone
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class NoShowAfterCommitTest {

    @Autowired AppointmentService appointmentService;
    @Autowired AvailabilitySlotRepository slotRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean AdvisorClient advisorClient;
    @MockitoBean UserClient userClient;
    @MockitoBean StrikeClient strikeClient;
    @MockitoBean KycCheck kycCheck;
    @MockitoBean NotificationPublisher notificationPublisher;
    @MockitoBean AuditRecorder auditRecorder;
    @MockitoBean AccountStatusService accountStatusService;

    private SlotTestData data;
    private BookingLookups lookups;
    private AppointmentTestData appointments;
    private long clientId;
    private long advisorUserId;
    private static final long ADMIN = 910_099L;
    private long appointmentId;

    @BeforeEach
    void setUp() {
        data = new SlotTestData(jdbcTemplate, slotRepository);
        lookups = new BookingLookups(jdbcTemplate);
        appointments = new AppointmentTestData(jdbcTemplate);
        long advisorId = data.anAdvisorId();
        clientId = lookups.aClientId();
        advisorUserId = lookups.userIdOfAdvisor(advisorId);
        AdvisorInfo info = new AdvisorInfo(advisorId, advisorUserId, true, "Late Advisor", "Planner", Specialty.GENERAL);
        when(advisorClient.getById(advisorId)).thenReturn(info);
        when(advisorClient.getByUserId(advisorUserId)).thenReturn(info);

        AvailabilitySlot slot = data.createSlot(advisorId, SlotStatus.AVAILABLE);
        appointments.startedHalfAnHourAgo(slot.getId());
        appointmentId = appointmentService.bookSlot(clientId, slot.getId(), lookups.freeServiceTypeId()).getId();
        appointments.setStatus(appointmentId, "CONFIRMED");
    }

    @AfterEach
    void deleteWhatThisTestCreated() {
        data.deleteCreated();
    }

    // no-show

    @Test
    void theNoShowStrikeIsAskedForAfterTheNoShowIsCommittedAndOutsideAnyTransaction() {
        AtomicBoolean inTransaction = new AtomicBoolean(true);
        AtomicReference<String> statusSeenByTheStrikeCall = new AtomicReference<>();
        when(strikeClient.addStrike(clientId)).thenAnswer(invocation -> {
            inTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
            statusSeenByTheStrikeCall.set(lookups.statusOfAppointment(appointmentId));
            return new StrikeResult(1, false);
        });

        appointmentService.markNoShow(advisorUserId, appointmentId);

        assertFalse(inTransaction.get(), "the strike call must not run inside a database transaction");
        assertEquals("NO_SHOW", statusSeenByTheStrikeCall.get(), "the no-show is already saved when the strike is asked for");
    }

    @Test
    void aFailedStrikeCallDoesNotUndoTheNoShow() {
        when(strikeClient.addStrike(clientId)).thenThrow(new ServiceUnavailable("The user service is not available, please try again later"));

        assertDoesNotThrow(() -> appointmentService.markNoShow(advisorUserId, appointmentId));

        assertEquals("NO_SHOW", lookups.statusOfAppointment(appointmentId));
        verify(auditRecorder).record(eq(AuditAction.APPOINTMENT_NO_SHOW), eq(advisorUserId), eq(appointmentId), anyString());
        verify(notificationPublisher).publish(eq(clientId), eq(NotificationType.APPOINTMENT_NO_SHOW), anyString(), eq(appointmentId));
    }

    @Test
    void aBlacklistingStrikeIsAudited() {
        when(strikeClient.addStrike(clientId)).thenReturn(new StrikeResult(3, true));

        appointmentService.markNoShow(advisorUserId, appointmentId);

        verify(auditRecorder).record(eq(AuditAction.CLIENT_BLACKLISTED), eq(clientId), eq(clientId), anyString());
        assertEquals("NO_SHOW", lookups.statusOfAppointment(appointmentId));
    }

    // excuse

    @Test
    void theStrikeIsRemovedAfterTheExcuseIsCommittedAndOutsideAnyTransaction() {
        appointments.setStatus(appointmentId, "NO_SHOW");
        AtomicBoolean inTransaction = new AtomicBoolean(true);
        AtomicReference<Boolean> excusedSeenByTheStrikeCall = new AtomicReference<>();
        when(strikeClient.removeStrike(clientId)).thenAnswer(invocation -> {
            inTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
            excusedSeenByTheStrikeCall.set(appointments.isExcused(appointmentId));
            return new StrikeResult(0, false);
        });

        appointmentService.excuseNoShow(ADMIN, appointmentId, "bad weather");

        assertFalse(inTransaction.get(), "the strike call must not run inside a database transaction");
        assertTrue(excusedSeenByTheStrikeCall.get(), "the excuse is already saved when the strike is removed");
        verify(auditRecorder).record(eq(AuditAction.NO_SHOW_EXCUSED), eq(ADMIN), eq(appointmentId), anyString());
        verify(notificationPublisher).publish(eq(clientId), eq(NotificationType.APPOINTMENT_NO_SHOW), anyString(), eq(appointmentId));
    }

    @Test
    void aFailedStrikeRemovalDoesNotUndoTheExcuse() {
        appointments.setStatus(appointmentId, "NO_SHOW");
        when(strikeClient.removeStrike(clientId)).thenThrow(new ServiceUnavailable("The user service is not available, please try again later"));

        assertDoesNotThrow(() -> appointmentService.excuseNoShow(ADMIN, appointmentId, "bad weather"));

        assertTrue(appointments.isExcused(appointmentId));
        verify(auditRecorder).record(eq(AuditAction.NO_SHOW_EXCUSED), eq(ADMIN), eq(appointmentId), anyString());
        verify(notificationPublisher).publish(anyLong(), eq(NotificationType.APPOINTMENT_NO_SHOW), anyString(), eq(appointmentId));
    }
}
