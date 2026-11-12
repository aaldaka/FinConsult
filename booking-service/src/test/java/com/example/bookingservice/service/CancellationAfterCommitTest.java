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
import com.example.bookingservice.model.Appointment;
import com.example.bookingservice.model.enums.AuditAction;
import com.example.bookingservice.model.enums.NotificationType;
import com.example.bookingservice.model.enums.SlotStatus;
import com.example.bookingservice.model.enums.Specialty;
import com.example.bookingservice.repository.AvailabilitySlotRepository;
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

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// runs on finconsult_test (TEST_DB_URL), no @Transactional on the class. The late cancellation happens on a slot
// that starts in 12 hours, so the client gets a strike and the slot is released again
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class CancellationAfterCommitTest {

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
    private long advisorId;
    private long clientId;
    private long appointmentId;
    private long slotId;

    @BeforeEach
    void setUp() {
        data = new SlotTestData(jdbcTemplate, slotRepository);
        lookups = new BookingLookups(jdbcTemplate);
        advisorId = data.anAdvisorId();
        clientId = lookups.aClientId();
        long advisorUserId = lookups.userIdOfAdvisor(advisorId);
        AdvisorInfo info = new AdvisorInfo(advisorId, advisorUserId, true, "Late Advisor", "Planner", Specialty.GENERAL);
        when(advisorClient.getById(advisorId)).thenReturn(info);

        AvailabilitySlot slot = data.createSlot(advisorId, SlotStatus.AVAILABLE);
        slot.setStartTime(LocalDateTime.now().plusHours(12)); // inside the 24 hour window, outside the last 6 hours
        slot.setEndTime(LocalDateTime.now().plusHours(13));
        slotRepository.save(slot);
        slotId = slot.getId();

        Appointment appointment = appointmentService.bookSlot(clientId, slotId, lookups.freeServiceTypeId());
        appointmentId = appointment.getId();
    }

    @AfterEach
    void deleteWhatThisTestCreated() {
        data.deleteCreated();
    }

    @Test
    void theStrikeIsAskedForAfterTheCancellationIsCommittedAndOutsideAnyTransaction() {
        AtomicBoolean inTransaction = new AtomicBoolean(true);
        AtomicReference<String> statusSeenByTheStrikeCall = new AtomicReference<>();
        when(strikeClient.addStrike(clientId)).thenAnswer(invocation -> {
            inTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
            statusSeenByTheStrikeCall.set(lookups.statusOfAppointment(appointmentId));
            return new StrikeResult(1, false);
        });

        appointmentService.cancelAppointment(appointmentId, clientId, null);

        assertFalse(inTransaction.get(), "the strike call must not run inside a database transaction");
        assertEquals("CANCELLED", statusSeenByTheStrikeCall.get(), "the cancellation is already saved when the strike is asked for");
        assertEquals("AVAILABLE", lookups.statusOfSlot(slotId));
    }

    @Test
    void aFailedStrikeCallDoesNotUndoTheCancellation() {
        when(strikeClient.addStrike(clientId)).thenThrow(new ServiceUnavailable("The user service is not available, please try again later"));

        assertDoesNotThrow(() -> appointmentService.cancelAppointment(appointmentId, clientId, "cannot come"));

        assertEquals("CANCELLED", lookups.statusOfAppointment(appointmentId));
        assertEquals("AVAILABLE", lookups.statusOfSlot(slotId));
        assertEquals(0, lookups.activeAppointmentsOnSlot(slotId));
        verify(auditRecorder).record(eq(AuditAction.APPOINTMENT_CANCELLED), eq(clientId), eq(appointmentId), anyString());
        verify(notificationPublisher).publish(anyLong(), eq(NotificationType.APPOINTMENT_CANCELLED), anyString(), eq(appointmentId));
    }

    @Test
    void aBlacklistingStrikeIsAudited() {
        when(strikeClient.addStrike(clientId)).thenReturn(new StrikeResult(3, true));

        appointmentService.cancelAppointment(appointmentId, clientId, null);

        verify(auditRecorder).record(eq(AuditAction.CLIENT_BLACKLISTED), eq(clientId), eq(clientId), anyString());
        assertEquals("CANCELLED", lookups.statusOfAppointment(appointmentId));
    }
}
