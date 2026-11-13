package com.example.bookingservice.service;

import com.example.bookingservice.client.AdvisorClient;
import com.example.bookingservice.client.AdvisorInfo;
import com.example.bookingservice.client.UserClient;
import com.example.bookingservice.exception.ServiceUnavailable;
import com.example.bookingservice.integration.NotificationPublisher;
import com.example.bookingservice.model.Appointment;
import com.example.bookingservice.model.AvailabilitySlot;
import com.example.bookingservice.model.enums.AppointmentStatus;
import com.example.bookingservice.model.enums.NotificationType;
import com.example.bookingservice.model.enums.SlotStatus;
import com.example.bookingservice.model.enums.Specialty;
import com.example.bookingservice.model.response.CancellationSummary;
import com.example.bookingservice.repository.AppointmentRepository;
import com.example.bookingservice.repository.AvailabilitySlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// no Spring and no database: the repositories, the user service and the stubs are mocks, and so is the transaction manager
@ExtendWith(MockitoExtension.class)
class InternalCancellationServiceTest {

    private static final long ADVISOR = 1L;
    private static final long ADVISOR_USER = 101L;
    private static final long CLIENT = 10L;

    @Mock AppointmentRepository appointmentRepository;
    @Mock AvailabilitySlotRepository slotRepository;
    @Mock AdvisorClient advisorClient;
    @Mock UserClient userClient;
    @Mock NotificationPublisher notifications;
    @Mock PlatformTransactionManager transactionManager;

    private InternalCancellationService service;
    private final Map<Long, Appointment> rows = new HashMap<>();

    @BeforeEach
    void setUp() {
        service = new InternalCancellationService(appointmentRepository, slotRepository, advisorClient, userClient, notifications, transactionManager);
        lenient().when(appointmentRepository.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(rows.get(inv.<Long>getArgument(0))));
        lenient().when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(advisorClient.getById(ADVISOR)).thenReturn(new AdvisorInfo(ADVISOR, ADVISOR_USER, true, "Advisor Name", "Planner", Specialty.GENERAL));
        lenient().when(userClient.names(any())).thenReturn(Map.of(CLIENT, "Client Name"));
    }

    private Appointment row(long id, long clientId, long advisorId, AppointmentStatus status) {
        AvailabilitySlot slot = new AvailabilitySlot();
        slot.setId(id);
        slot.setStartTime(LocalDateTime.now().plusDays(1));
        slot.setEndTime(LocalDateTime.now().plusDays(1).plusHours(1));
        slot.setStatus(SlotStatus.BOOKED);
        Appointment appointment = new Appointment();
        appointment.setId(id);
        appointment.setClientId(clientId);
        appointment.setAdvisorId(advisorId);
        appointment.setSlot(slot);
        appointment.setStatus(status);
        rows.put(id, appointment);
        return appointment;
    }

    @Test
    void anAdvisorsActiveAppointmentsAreCancelledTheSlotsFreedAndTheClientsTold() {
        Appointment pending = row(1, CLIENT, ADVISOR, AppointmentStatus.PENDING);
        Appointment confirmed = row(2, 11, ADVISOR, AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findByAdvisorIdAndStatusIn(eq(ADVISOR), any())).thenReturn(List.of(pending, confirmed));

        CancellationSummary result = service.cancelForAdvisor(ADVISOR, null, null);

        assertEquals(new CancellationSummary(2, 0), result);
        for (Appointment a : List.of(pending, confirmed)) {
            assertEquals(AppointmentStatus.CANCELLED, a.getStatus());
            assertEquals("Advisor is no longer available", a.getCancellationReason());
            assertNotNull(a.getCancelledAt());
            assertEquals(SlotStatus.AVAILABLE, a.getSlot().getStatus());
        }
        verify(notifications).publish(eq(CLIENT), eq(NotificationType.APPOINTMENT_CANCELLED), contains("Advisor Name cancelled the appointment"), eq(1L));
        verify(notifications).publish(eq(11L), eq(NotificationType.APPOINTMENT_CANCELLED), anyString(), eq(2L));
    }

    @Test
    void aClientsActiveAppointmentsAreCancelledAndEachAdvisorIsTold() {
        Appointment first = row(1, CLIENT, ADVISOR, AppointmentStatus.CONFIRMED);
        Appointment second = row(2, CLIENT, 2, AppointmentStatus.PENDING);
        when(appointmentRepository.findByClientIdAndStatusIn(eq(CLIENT), any())).thenReturn(List.of(first, second));
        when(advisorClient.lookup(any())).thenReturn(Map.of(
                ADVISOR, new AdvisorInfo(ADVISOR, ADVISOR_USER, true, "Advisor Name", "Planner", Specialty.GENERAL),
                2L, new AdvisorInfo(2L, 202L, true, "Second", "Planner", Specialty.GENERAL)));

        assertEquals(new CancellationSummary(2, 0), service.cancelForClient(CLIENT, null, null));

        assertEquals("Client account was deactivated", first.getCancellationReason());
        assertEquals(SlotStatus.AVAILABLE, second.getSlot().getStatus());
        verify(notifications).publish(eq(ADVISOR_USER), eq(NotificationType.APPOINTMENT_CANCELLED), contains("Your appointment with Client Name"), eq(1L));
        verify(notifications).publish(eq(202L), eq(NotificationType.APPOINTMENT_CANCELLED), anyString(), eq(2L));
    }

    @Test
    void aConflictIsCountedAsFailedAndTheRestStillCancel() {
        Appointment first = row(1, CLIENT, ADVISOR, AppointmentStatus.PENDING);
        Appointment conflicting = row(2, CLIENT, ADVISOR, AppointmentStatus.PENDING);
        Appointment last = row(3, CLIENT, ADVISOR, AppointmentStatus.PENDING);
        when(appointmentRepository.findByAdvisorIdAndStatusIn(eq(ADVISOR), any())).thenReturn(List.of(first, conflicting, last));
        doThrow(new ObjectOptimisticLockingFailureException(Appointment.class, 2L))
                .when(appointmentRepository).save(argThat((Appointment a) -> a != null && a.getId() == 2L));

        assertEquals(new CancellationSummary(2, 1), service.cancelForAdvisor(ADVISOR, null, null));

        assertEquals(AppointmentStatus.CANCELLED, first.getStatus());
        assertEquals(AppointmentStatus.CANCELLED, last.getStatus());
        verify(transactionManager, times(3)).getTransaction(any());
        verify(transactionManager, times(2)).commit(any());
        verify(transactionManager).rollback(any());
        verify(notifications, never()).publish(anyLong(), any(), anyString(), eq(2L));
    }

    @Test
    void anAppointmentThatChangedSinceTheListIsLeftAloneAndNotCounted() {
        Appointment stale = row(1, CLIENT, ADVISOR, AppointmentStatus.PENDING);
        Appointment fresh = row(2, CLIENT, ADVISOR, AppointmentStatus.PENDING);
        when(appointmentRepository.findByAdvisorIdAndStatusIn(eq(ADVISOR), any())).thenReturn(List.of(stale, fresh));
        stale.setStatus(AppointmentStatus.COMPLETED); // what the transaction reads now

        assertEquals(new CancellationSummary(1, 0), service.cancelForAdvisor(ADVISOR, null, null));

        assertEquals(AppointmentStatus.COMPLETED, stale.getStatus());
        assertEquals(SlotStatus.BOOKED, stale.getSlot().getStatus());
    }

    @Test
    void nothingToCancelIsZeroAndZero() {
        when(appointmentRepository.findByClientIdAndStatusIn(eq(CLIENT), any())).thenReturn(List.of());

        assertEquals(new CancellationSummary(0, 0), service.cancelForClient(CLIENT, null, null));
    }

    @Test
    void aUserServiceThatIsDownDoesNotStopTheCascadeOrUndoIt() {
        Appointment appointment = row(1, CLIENT, ADVISOR, AppointmentStatus.PENDING);
        when(appointmentRepository.findByClientIdAndStatusIn(eq(CLIENT), any())).thenReturn(List.of(appointment));
        when(advisorClient.lookup(any())).thenThrow(new ServiceUnavailable("down"));
        when(userClient.names(any())).thenThrow(new ServiceUnavailable("down"));

        assertEquals(new CancellationSummary(1, 0), service.cancelForClient(CLIENT, null, null));

        assertEquals(AppointmentStatus.CANCELLED, appointment.getStatus());
        verify(notifications, never()).publish(anyLong(), any(), anyString(), anyLong()); // nobody to address it to
    }

    @Test
    void aFailingNotificationDoesNotCountAsAFailure() {
        Appointment appointment = row(1, CLIENT, ADVISOR, AppointmentStatus.PENDING);
        when(appointmentRepository.findByAdvisorIdAndStatusIn(eq(ADVISOR), any())).thenReturn(List.of(appointment));
        doThrow(new RuntimeException("boom")).when(notifications).publish(anyLong(), any(), anyString(), anyLong());

        assertEquals(new CancellationSummary(1, 0), service.cancelForAdvisor(ADVISOR, null, null));
    }

    // the optional reason

    @Test
    void aGivenReasonReplacesTheAdvisorDefaultInTheCancellationAndTheNotification() {
        Appointment appointment = row(1, CLIENT, ADVISOR, AppointmentStatus.PENDING);
        when(appointmentRepository.findByAdvisorIdAndStatusIn(eq(ADVISOR), any())).thenReturn(List.of(appointment));

        service.cancelForAdvisor(ADVISOR, "  Left the company  ", 99L);

        assertEquals("Left the company", appointment.getCancellationReason());
        verify(notifications).publish(eq(CLIENT), eq(NotificationType.APPOINTMENT_CANCELLED), contains("Reason: Left the company"), eq(1L));
    }

    @Test
    void aGivenReasonReplacesTheClientDefaultInTheCancellationAndTheNotification() {
        Appointment appointment = row(1, CLIENT, ADVISOR, AppointmentStatus.CONFIRMED);
        when(appointmentRepository.findByClientIdAndStatusIn(eq(CLIENT), any())).thenReturn(List.of(appointment));
        when(advisorClient.lookup(any())).thenReturn(Map.of(ADVISOR, new AdvisorInfo(ADVISOR, ADVISOR_USER, true, "Advisor Name", "Planner", Specialty.GENERAL)));

        service.cancelForClient(CLIENT, "Fraud review", 99L);

        assertEquals("Fraud review", appointment.getCancellationReason());
        verify(notifications).publish(eq(ADVISOR_USER), eq(NotificationType.APPOINTMENT_CANCELLED), contains("Reason: Fraud review"), eq(1L));
    }

    @Test
    void aMissingOrBlankReasonFallsBackToTheDefaults() {
        for (String reason : new String[]{null, "", "   "}) {
            Appointment forAdvisor = row(1, CLIENT, ADVISOR, AppointmentStatus.PENDING);
            when(appointmentRepository.findByAdvisorIdAndStatusIn(eq(ADVISOR), any())).thenReturn(List.of(forAdvisor));
            service.cancelForAdvisor(ADVISOR, reason, null);
            assertEquals("Advisor is no longer available", forAdvisor.getCancellationReason());

            Appointment forClient = row(2, CLIENT, ADVISOR, AppointmentStatus.PENDING);
            when(appointmentRepository.findByClientIdAndStatusIn(eq(CLIENT), any())).thenReturn(List.of(forClient));
            service.cancelForClient(CLIENT, reason, null);
            assertEquals("Client account was deactivated", forClient.getCancellationReason());
        }
    }
}
