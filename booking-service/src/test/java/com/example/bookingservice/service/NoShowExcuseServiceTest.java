package com.example.bookingservice.service;

import com.example.bookingservice.client.AdvisorClient;
import com.example.bookingservice.client.AdvisorInfo;
import com.example.bookingservice.client.StrikeClient;
import com.example.bookingservice.client.StrikeResult;
import com.example.bookingservice.client.UserClient;
import com.example.bookingservice.exception.AccountNotActive;
import com.example.bookingservice.exception.InformationNotFound;
import com.example.bookingservice.exception.InvalidRequest;
import com.example.bookingservice.exception.ServiceUnavailable;
import com.example.bookingservice.integration.AuditRecorder;
import com.example.bookingservice.integration.KycCheck;
import com.example.bookingservice.integration.NotificationPublisher;
import com.example.bookingservice.model.Appointment;
import com.example.bookingservice.model.AvailabilitySlot;
import com.example.bookingservice.model.ServiceType;
import com.example.bookingservice.model.enums.AppointmentStatus;
import com.example.bookingservice.model.enums.AuditAction;
import com.example.bookingservice.model.enums.NotificationType;
import com.example.bookingservice.model.enums.SlotStatus;
import com.example.bookingservice.model.enums.Specialty;
import com.example.bookingservice.repository.AppointmentRepository;
import com.example.bookingservice.repository.AvailabilitySlotRepository;
import com.example.bookingservice.repository.ServiceTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// no Spring and no database: repositories, clients and stubs are mocks, and the transaction manager is a mock
// so the order "transaction commits, then the strike call" can be checked
@ExtendWith(MockitoExtension.class)
class NoShowExcuseServiceTest {

    private static final long CLIENT = 10L;
    private static final long ADVISOR = 1L;
    private static final long ADVISOR_USER = 101L;
    private static final long ADMIN = 99L;
    private static final long SLOT = 1L;
    private static final long APPOINTMENT = 77L;

    @Mock AppointmentRepository appointmentRepository;
    @Mock AvailabilitySlotRepository slotRepository;
    @Mock ServiceTypeRepository serviceTypeRepository;
    @Mock AdvisorClient advisorClient;
    @Mock UserClient userClient;
    @Mock StrikeClient strikeClient;
    @Mock KycCheck kycCheck;
    @Mock NotificationPublisher notifications;
    @Mock AuditRecorder audit;
    @Mock AccountStatusService accountStatusService;
    @Mock PlatformTransactionManager transactionManager;

    private AppointmentService service;

    @BeforeEach
    void setUp() {
        service = new AppointmentService(appointmentRepository, slotRepository, serviceTypeRepository, advisorClient, userClient,
                strikeClient, kycCheck, notifications, audit, accountStatusService, transactionManager);
    }

    // helpers

    private AdvisorInfo advisor() {
        return new AdvisorInfo(ADVISOR, ADVISOR_USER, true, "Advisor Name", "Planner", Specialty.GENERAL);
    }

    private AvailabilitySlot slotStartedMinutesAgo(long minutes) {
        AvailabilitySlot slot = new AvailabilitySlot();
        slot.setId(SLOT);
        slot.setAdvisorId(ADVISOR);
        slot.setStartTime(LocalDateTime.now().minusMinutes(minutes));
        slot.setEndTime(LocalDateTime.now().minusMinutes(minutes).plusHours(1));
        slot.setStatus(SlotStatus.BOOKED);
        return slot;
    }

    private Appointment appointment(AppointmentStatus status, AvailabilitySlot slot) {
        ServiceType type = new ServiceType();
        type.setName("Retirement review");
        Appointment appointment = new Appointment();
        appointment.setId(APPOINTMENT);
        appointment.setClientId(CLIENT);
        appointment.setAdvisorId(ADVISOR);
        appointment.setSlot(slot);
        appointment.setServiceType(type);
        appointment.setStatus(status);
        return appointment;
    }

    private Appointment existing(AppointmentStatus status, AvailabilitySlot slot) {
        Appointment appointment = appointment(status, slot);
        lenient().when(advisorClient.getByUserId(ADVISOR_USER)).thenReturn(advisor());
        lenient().when(appointmentRepository.findById(APPOINTMENT)).thenReturn(Optional.of(appointment));
        lenient().when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));
        return appointment;
    }

    // a confirmed appointment that started 30 minutes ago, so the 20 minute grace has passed
    private Appointment confirmedAndOverdue() {
        return existing(AppointmentStatus.CONFIRMED, slotStartedMinutesAgo(30));
    }

    private Appointment noShow() {
        return existing(AppointmentStatus.NO_SHOW, slotStartedMinutesAgo(120));
    }

    // no-show

    @Test
    void markingATooRecentAppointmentFails() {
        Appointment appointment = existing(AppointmentStatus.CONFIRMED, slotStartedMinutesAgo(5));

        assertEquals("Cannot mark No Show until 20 minutes has passed from the appointment start time",
                assertThrows(InvalidRequest.class, () -> service.markNoShow(ADVISOR_USER, APPOINTMENT)).getMessage());

        assertEquals(AppointmentStatus.CONFIRMED, appointment.getStatus());
        verifyNoInteractions(strikeClient, notifications, audit);
    }

    @Test
    void theOwningAdvisorMarksAConfirmedAppointmentAndTheClientGetsAStrike() {
        Appointment appointment = confirmedAndOverdue();
        when(strikeClient.addStrike(CLIENT)).thenReturn(new StrikeResult(1, false));

        Appointment result = service.markNoShow(ADVISOR_USER, APPOINTMENT);

        assertSame(appointment, result);
        assertEquals(AppointmentStatus.NO_SHOW, result.getStatus());
        verify(appointmentRepository).save(appointment);
        verify(strikeClient).addStrike(CLIENT);
        verify(audit).record(eq(AuditAction.APPOINTMENT_NO_SHOW), eq(ADVISOR_USER), eq(APPOINTMENT), contains("strikes: 1/3"));
        verify(notifications).publish(eq(CLIENT), eq(NotificationType.APPOINTMENT_NO_SHOW), contains("Strikes: 1/3"), eq(APPOINTMENT));
        verify(audit, never()).record(eq(AuditAction.CLIENT_BLACKLISTED), anyLong(), anyLong(), anyString());
    }

    @Test
    void theStrikeCallComesAfterTheTransactionCommitsAndTheStubsAfterTheStrike() {
        confirmedAndOverdue();
        when(strikeClient.addStrike(CLIENT)).thenReturn(new StrikeResult(1, false));

        service.markNoShow(ADVISOR_USER, APPOINTMENT);

        InOrder order = inOrder(accountStatusService, advisorClient, transactionManager, strikeClient, audit, notifications);
        order.verify(accountStatusService).requireActive(ADVISOR_USER);
        order.verify(advisorClient).getByUserId(ADVISOR_USER);
        order.verify(transactionManager).getTransaction(any());
        order.verify(transactionManager).commit(any());
        order.verify(strikeClient).addStrike(CLIENT);
        order.verify(audit).record(eq(AuditAction.APPOINTMENT_NO_SHOW), anyLong(), anyLong(), anyString());
        order.verify(notifications).publish(anyLong(), any(), anyString(), anyLong());
    }

    @Test
    void aBlacklistingStrikeIsAuditedAndNamesTheClient() {
        confirmedAndOverdue();
        when(userClient.names(any())).thenReturn(Map.of(CLIENT, "Test Client"));
        when(strikeClient.addStrike(CLIENT)).thenReturn(new StrikeResult(3, true));

        service.markNoShow(ADVISOR_USER, APPOINTMENT);

        verify(audit).record(eq(AuditAction.CLIENT_BLACKLISTED), eq(CLIENT), eq(CLIENT), contains("Test Client blacklisted after reaching 3 strikes"));
        verify(notifications).publish(eq(CLIENT), eq(NotificationType.APPOINTMENT_NO_SHOW), contains("Strikes: 3/3"), eq(APPOINTMENT));
    }

    @Test
    void aFailedStrikeCallKeepsTheNoShowAndStillAnnouncesIt() {
        Appointment appointment = confirmedAndOverdue();
        when(strikeClient.addStrike(CLIENT)).thenThrow(new ServiceUnavailable("The user service is not available, please try again later"));

        Appointment result = assertDoesNotThrow(() -> service.markNoShow(ADVISOR_USER, APPOINTMENT));

        assertEquals(AppointmentStatus.NO_SHOW, appointment.getStatus());
        assertSame(appointment, result);
        verify(transactionManager).commit(any());
        verify(transactionManager, never()).rollback(any());
        verify(audit).record(eq(AuditAction.APPOINTMENT_NO_SHOW), eq(ADVISOR_USER), eq(APPOINTMENT), anyString());
        verify(notifications).publish(eq(CLIENT), eq(NotificationType.APPOINTMENT_NO_SHOW), anyString(), eq(APPOINTMENT));
        verify(audit, never()).record(eq(AuditAction.CLIENT_BLACKLISTED), anyLong(), anyLong(), anyString());
    }

    @Test
    void aFailedStrikeCallLeavesTheStrikeCountOutOfTheMessages() {
        confirmedAndOverdue();
        when(strikeClient.addStrike(CLIENT)).thenThrow(new ServiceUnavailable("down"));

        service.markNoShow(ADVISOR_USER, APPOINTMENT);

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(audit).record(eq(AuditAction.APPOINTMENT_NO_SHOW), anyLong(), anyLong(), detail.capture());
        assertFalse(detail.getValue().contains("strikes"));
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notifications).publish(anyLong(), any(), message.capture(), anyLong());
        assertFalse(message.getValue().contains("Strikes"));
    }

    @Test
    void aFailingNotificationOrNameLookupDoesNotUndoTheNoShow() {
        Appointment appointment = confirmedAndOverdue();
        when(strikeClient.addStrike(CLIENT)).thenReturn(new StrikeResult(1, false));
        when(userClient.names(any())).thenThrow(new ServiceUnavailable("down"));
        doThrow(new RuntimeException("boom")).when(notifications).publish(anyLong(), any(), anyString(), anyLong());
        doThrow(new RuntimeException("boom")).when(audit).record(any(), anyLong(), anyLong(), anyString());

        assertDoesNotThrow(() -> service.markNoShow(ADVISOR_USER, APPOINTMENT));

        assertEquals(AppointmentStatus.NO_SHOW, appointment.getStatus());
        verify(transactionManager, never()).rollback(any());
    }

    @Test
    void anotherAdvisorCannotMarkItAndNoStrikeIsAdded() {
        Appointment appointment = confirmedAndOverdue();
        when(advisorClient.getByUserId(202L)).thenReturn(new AdvisorInfo(2L, 202L, true, "Other", "Planner", Specialty.GENERAL));

        assertEquals("You can only mark your own appointments",
                assertThrows(InvalidRequest.class, () -> service.markNoShow(202L, APPOINTMENT)).getMessage());

        assertEquals(AppointmentStatus.CONFIRMED, appointment.getStatus());
        verifyNoInteractions(strikeClient, notifications, audit);
    }

    @Test
    void onlyAConfirmedAppointmentCanBeMarked() {
        for (AppointmentStatus status : List.of(AppointmentStatus.PENDING, AppointmentStatus.CANCELLED,
                AppointmentStatus.COMPLETED, AppointmentStatus.NO_SHOW)) {
            Appointment appointment = existing(status, slotStartedMinutesAgo(30));

            assertEquals("Only confirmed appointments can be marked as no-show",
                    assertThrows(InvalidRequest.class, () -> service.markNoShow(ADVISOR_USER, APPOINTMENT)).getMessage());

            assertEquals(status, appointment.getStatus());
        }
        verifyNoInteractions(strikeClient);
    }

    @Test
    void markingAnUnknownAppointmentIs404() {
        when(advisorClient.getByUserId(ADVISOR_USER)).thenReturn(advisor());
        when(appointmentRepository.findById(APPOINTMENT)).thenReturn(Optional.empty());

        assertEquals("Appointment not found",
                assertThrows(InformationNotFound.class, () -> service.markNoShow(ADVISOR_USER, APPOINTMENT)).getMessage());

        verifyNoInteractions(strikeClient);
    }

    @Test
    void aCallerWithoutAnAdvisorProfileIs404AndNoTransactionOpens() {
        when(advisorClient.getByUserId(ADVISOR_USER)).thenThrow(new InformationNotFound("Advisor profile not found"));

        assertThrows(InformationNotFound.class, () -> service.markNoShow(ADVISOR_USER, APPOINTMENT));

        verifyNoInteractions(transactionManager, appointmentRepository, strikeClient);
    }

    @Test
    void markingChecksTheAccountFirst() {
        doThrow(new AccountNotActive("Your account is blacklisted")).when(accountStatusService).requireActive(ADVISOR_USER);

        assertThrows(AccountNotActive.class, () -> service.markNoShow(ADVISOR_USER, APPOINTMENT));

        verifyNoInteractions(advisorClient, appointmentRepository, transactionManager, strikeClient, notifications, audit);
    }

    @Test
    void aMarkThatFailsInsideTheTransactionCallsNoStrike() {
        confirmedAndOverdue();
        doThrow(new ObjectOptimisticLockingFailureException(Appointment.class, APPOINTMENT)).when(appointmentRepository).save(any(Appointment.class));

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> service.markNoShow(ADVISOR_USER, APPOINTMENT));

        verifyNoInteractions(strikeClient, notifications, audit);
    }

    // excuse

    @Test
    void excusingANoShowRemovesTheStrike() {
        Appointment appointment = noShow();
        when(strikeClient.removeStrike(CLIENT)).thenReturn(new StrikeResult(2, false));

        Appointment result = service.excuseNoShow(ADMIN, APPOINTMENT, "mistake");

        assertSame(appointment, result);
        assertTrue(result.isExcused());
        assertEquals("mistake", result.getExcuseReason());
        assertEquals(AppointmentStatus.NO_SHOW, result.getStatus());
        verify(strikeClient).removeStrike(CLIENT);
        verify(audit).record(eq(AuditAction.NO_SHOW_EXCUSED), eq(ADMIN), eq(APPOINTMENT), contains("mistake; strikes: 2/3"));
        verify(notifications).publish(eq(CLIENT), eq(NotificationType.APPOINTMENT_NO_SHOW), contains("Your no-show was excused. Reason: mistake"), eq(APPOINTMENT));
    }

    @Test
    void excusingTwiceFailsAndRemovesNothing() {
        Appointment appointment = noShow();
        appointment.setExcused(true);

        assertEquals("This no-show was already excused",
                assertThrows(InvalidRequest.class, () -> service.excuseNoShow(ADMIN, APPOINTMENT, "mistake")).getMessage());

        verify(strikeClient, never()).removeStrike(anyLong());
        verifyNoInteractions(notifications, audit);
    }

    @Test
    void aReasonIsRequired() {
        Appointment appointment = noShow();

        for (String reason : new String[]{null, "", "   "}) {
            assertEquals("A reason is required",
                    assertThrows(InvalidRequest.class, () -> service.excuseNoShow(ADMIN, APPOINTMENT, reason)).getMessage());
        }

        assertFalse(appointment.isExcused());
        verifyNoInteractions(strikeClient);
    }

    @Test
    void onlyANoShowCanBeExcused() {
        for (AppointmentStatus status : List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED,
                AppointmentStatus.CANCELLED, AppointmentStatus.COMPLETED)) {
            existing(status, slotStartedMinutesAgo(120));

            assertEquals("Appointment was not marked as a No-Show",
                    assertThrows(InvalidRequest.class, () -> service.excuseNoShow(ADMIN, APPOINTMENT, "mistake")).getMessage());
        }
        verifyNoInteractions(strikeClient);
    }

    @Test
    void excusingAnUnknownAppointmentIs404EvenWithoutAReason() {
        when(appointmentRepository.findById(APPOINTMENT)).thenReturn(Optional.empty());

        assertEquals("Appointment not found",
                assertThrows(InformationNotFound.class, () -> service.excuseNoShow(ADMIN, APPOINTMENT, null)).getMessage());

        verifyNoInteractions(strikeClient);
    }

    @Test
    void theStrikeIsRemovedAfterTheTransactionCommits() {
        noShow();
        when(strikeClient.removeStrike(CLIENT)).thenReturn(new StrikeResult(0, false));

        service.excuseNoShow(ADMIN, APPOINTMENT, "mistake");

        InOrder order = inOrder(accountStatusService, transactionManager, strikeClient, audit, notifications);
        order.verify(accountStatusService).requireActive(ADMIN);
        order.verify(transactionManager).getTransaction(any());
        order.verify(transactionManager).commit(any());
        order.verify(strikeClient).removeStrike(CLIENT);
        order.verify(audit).record(eq(AuditAction.NO_SHOW_EXCUSED), anyLong(), anyLong(), anyString());
        order.verify(notifications).publish(anyLong(), any(), anyString(), anyLong());
    }

    @Test
    void aFailedStrikeRemovalKeepsTheExcuseAndStillAnnouncesIt() {
        Appointment appointment = noShow();
        when(strikeClient.removeStrike(CLIENT)).thenThrow(new ServiceUnavailable("The user service is not available, please try again later"));

        Appointment result = assertDoesNotThrow(() -> service.excuseNoShow(ADMIN, APPOINTMENT, "mistake"));

        assertTrue(appointment.isExcused());
        assertSame(appointment, result);
        verify(transactionManager).commit(any());
        verify(transactionManager, never()).rollback(any());
        verify(audit).record(eq(AuditAction.NO_SHOW_EXCUSED), eq(ADMIN), eq(APPOINTMENT), anyString());
        verify(notifications).publish(eq(CLIENT), eq(NotificationType.APPOINTMENT_NO_SHOW), anyString(), eq(APPOINTMENT));
    }

    @Test
    void aFailingNotificationOrAuditDoesNotUndoTheExcuse() {
        Appointment appointment = noShow();
        when(strikeClient.removeStrike(CLIENT)).thenReturn(new StrikeResult(2, false));
        when(userClient.names(any())).thenThrow(new ServiceUnavailable("down"));
        doThrow(new RuntimeException("boom")).when(notifications).publish(anyLong(), any(), anyString(), anyLong());
        doThrow(new RuntimeException("boom")).when(audit).record(any(), anyLong(), anyLong(), anyString());

        assertDoesNotThrow(() -> service.excuseNoShow(ADMIN, APPOINTMENT, "mistake"));

        assertTrue(appointment.isExcused());
        verify(transactionManager, never()).rollback(any());
    }

    @Test
    void anExcuseThatFailsInsideTheTransactionRemovesNoStrike() {
        noShow();
        doThrow(new ObjectOptimisticLockingFailureException(Appointment.class, APPOINTMENT)).when(appointmentRepository).save(any(Appointment.class));

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> service.excuseNoShow(ADMIN, APPOINTMENT, "mistake"));

        verifyNoInteractions(strikeClient, notifications, audit);
    }

    @Test
    void excusingChecksTheAccountFirst() {
        doThrow(new AccountNotActive("Your account is not active")).when(accountStatusService).requireActive(ADMIN);

        assertThrows(AccountNotActive.class, () -> service.excuseNoShow(ADMIN, APPOINTMENT, "mistake"));

        verifyNoInteractions(appointmentRepository, transactionManager, strikeClient, notifications, audit);
    }

    // listings

    @Test
    void aClientGetsTheirOwnAppointmentsWithOrWithoutAStatus() {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        List<Appointment> all = List.of(appointment(AppointmentStatus.PENDING, slotStartedMinutesAgo(-60)));
        List<Appointment> confirmed = List.of(appointment(AppointmentStatus.CONFIRMED, slotStartedMinutesAgo(-120)));
        when(appointmentRepository.findByClientId(CLIENT, sort)).thenReturn(all);
        when(appointmentRepository.findByClientIdAndStatus(CLIENT, AppointmentStatus.CONFIRMED, sort)).thenReturn(confirmed);

        assertSame(all, service.getClientAppointments(CLIENT, null, sort));
        assertSame(confirmed, service.getClientAppointments(CLIENT, AppointmentStatus.CONFIRMED, sort));

        verifyNoInteractions(advisorClient);
    }

    @Test
    void anAdvisorGetsTheirOwnAppointmentsByTheAdvisorIdFromTheUserService() {
        Sort sort = Sort.by("slot.startTime");
        List<Appointment> all = List.of(appointment(AppointmentStatus.PENDING, slotStartedMinutesAgo(-60)));
        List<Appointment> cancelled = List.of(appointment(AppointmentStatus.CANCELLED, slotStartedMinutesAgo(-120)));
        when(advisorClient.getByUserId(ADVISOR_USER)).thenReturn(advisor());
        when(appointmentRepository.findByAdvisorId(ADVISOR, sort)).thenReturn(all);
        when(appointmentRepository.findByAdvisorIdAndStatus(ADVISOR, AppointmentStatus.CANCELLED, sort)).thenReturn(cancelled);

        assertSame(all, service.getAdvisorAppointments(ADVISOR_USER, null, sort));
        assertSame(cancelled, service.getAdvisorAppointments(ADVISOR_USER, AppointmentStatus.CANCELLED, sort));
    }

    @Test
    void anAdvisorListingWithoutAnAdvisorProfileIs404() {
        when(advisorClient.getByUserId(ADVISOR_USER)).thenThrow(new InformationNotFound("Advisor profile not found"));

        assertThrows(InformationNotFound.class, () -> service.getAdvisorAppointments(ADVISOR_USER, null, Sort.by("createdAt")));

        verifyNoInteractions(appointmentRepository);
    }

    @Test
    void onlyTheThreeAllowedSortFieldsAreAccepted() {
        for (String field : new String[]{"createdAt", "status", "slot.startTime"}) {
            assertDoesNotThrow(() -> service.getClientAppointments(CLIENT, null, Sort.by(field)));
        }

        for (String field : new String[]{"clientId", "version", "notes", "slot.id"}) {
            InvalidRequest e = assertThrows(InvalidRequest.class, () -> service.getClientAppointments(CLIENT, null, Sort.by(field)));
            assertTrue(e.getMessage().startsWith("Cannot sort by " + field + ". Allowed: "));
            assertThrows(InvalidRequest.class, () -> service.getAdvisorAppointments(ADVISOR_USER, null, Sort.by(field)));
        }
        verify(appointmentRepository, never()).findByClientId(eq(CLIENT), eq(Sort.by("version")));
    }

    @Test
    void theNoShowListIsLatestFirstAndIncludesExcusedOnes() {
        List<Appointment> noShows = List.of(noShow());
        when(appointmentRepository.findByStatus(AppointmentStatus.NO_SHOW, Sort.by("slot.startTime").descending())).thenReturn(noShows);

        assertSame(noShows, service.getNoShows());
    }
}
