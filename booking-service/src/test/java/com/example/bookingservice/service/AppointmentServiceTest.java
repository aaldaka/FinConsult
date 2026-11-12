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
import com.example.bookingservice.exception.SlotUnavailable;
import com.example.bookingservice.integration.AuditRecorder;
import com.example.bookingservice.integration.KycCheck;
import com.example.bookingservice.integration.NotificationPublisher;
import com.example.bookingservice.model.Appointment;
import com.example.bookingservice.model.AvailabilitySlot;
import com.example.bookingservice.model.ServiceType;
import com.example.bookingservice.model.enums.AppointmentStatus;
import com.example.bookingservice.model.enums.AuditAction;
import com.example.bookingservice.model.enums.DocumentType;
import com.example.bookingservice.model.enums.NotificationType;
import com.example.bookingservice.model.enums.SlotStatus;
import com.example.bookingservice.model.enums.Specialty;
import com.example.bookingservice.repository.AppointmentRepository;
import com.example.bookingservice.repository.AvailabilitySlotRepository;
import com.example.bookingservice.repository.ServiceTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
class AppointmentServiceTest {

    private static final long CLIENT = 10L;
    private static final long ADVISOR = 1L;
    private static final long ADVISOR_USER = 101L;
    private static final long SLOT = 1L;
    private static final long SERVICE = 5L;
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

    private AdvisorInfo advisor(Specialty specialty, boolean active) {
        return new AdvisorInfo(ADVISOR, ADVISOR_USER, active, "Advisor Name", "Planner", specialty);
    }

    private AvailabilitySlot slot(LocalDateTime start, SlotStatus status) {
        AvailabilitySlot slot = new AvailabilitySlot();
        slot.setId(SLOT);
        slot.setAdvisorId(ADVISOR);
        slot.setStartTime(start);
        slot.setEndTime(start.plusHours(1));
        slot.setStatus(status);
        return slot;
    }

    private ServiceType serviceType(boolean requiresKyc, DocumentType document, Specialty specialty, boolean active) {
        ServiceType type = new ServiceType();
        type.setId(SERVICE);
        type.setName("Retirement review");
        type.setRequiresKyc(requiresKyc);
        type.setRequiredDocumentType(document);
        type.setRequiredSpecialty(specialty);
        type.setActive(active);
        return type;
    }

    private ServiceType freeService() {
        return serviceType(false, null, null, true);
    }

    private void bookable(AvailabilitySlot slot, ServiceType type, AdvisorInfo advisor) {
        lenient().when(slotRepository.findAdvisorIdById(SLOT)).thenReturn(Optional.of(ADVISOR));
        lenient().when(advisorClient.getById(ADVISOR)).thenReturn(advisor);
        lenient().when(slotRepository.findByIdForUpdate(SLOT)).thenReturn(Optional.of(slot));
        lenient().when(serviceTypeRepository.findById(SERVICE)).thenReturn(Optional.of(type));
        lenient().when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(APPOINTMENT);
            }
            return saved;
        });
    }

    private Appointment appointment(AppointmentStatus status, AvailabilitySlot slot) {
        Appointment appointment = new Appointment();
        appointment.setId(APPOINTMENT);
        appointment.setClientId(CLIENT);
        appointment.setAdvisorId(ADVISOR);
        appointment.setSlot(slot);
        appointment.setServiceType(freeService());
        appointment.setStatus(status);
        return appointment;
    }

    private Appointment existing(AppointmentStatus status, AvailabilitySlot slot) {
        Appointment appointment = appointment(status, slot);
        lenient().when(appointmentRepository.findAdvisorIdById(APPOINTMENT)).thenReturn(Optional.of(ADVISOR));
        lenient().when(advisorClient.getById(ADVISOR)).thenReturn(advisor(Specialty.GENERAL, true));
        lenient().when(appointmentRepository.findById(APPOINTMENT)).thenReturn(Optional.of(appointment));
        lenient().when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));
        return appointment;
    }

    private AvailabilitySlot bookedSlotStartingIn(long hours) {
        return slot(LocalDateTime.now().plusHours(hours), SlotStatus.BOOKED);
    }

    // book

    @Test
    void bookingAnAvailableSlotCreatesAPendingAppointmentAndBooksTheSlot() {
        AvailabilitySlot slot = slot(LocalDateTime.now().plusDays(1), SlotStatus.AVAILABLE);
        bookable(slot, freeService(), advisor(Specialty.GENERAL, true));

        Appointment result = service.bookSlot(CLIENT, SLOT, SERVICE);

        assertEquals(AppointmentStatus.PENDING, result.getStatus());
        assertEquals(CLIENT, result.getClientId());
        assertEquals(ADVISOR, result.getAdvisorId());
        assertEquals(SlotStatus.BOOKED, slot.getStatus());
        verify(slotRepository).save(slot);
        verifyNoInteractions(kycCheck, strikeClient);
        verify(notifications).publish(eq(ADVISOR_USER), eq(NotificationType.APPOINTMENT_BOOKED), contains("Retirement review"), eq(APPOINTMENT));
        verify(audit).record(eq(AuditAction.APPOINTMENT_BOOKED), eq(CLIENT), eq(APPOINTMENT), contains("Advisor Name"));
    }

    @Test
    void theAccountIsCheckedFirstAndARefusedCallerTouchesNothing() {
        doThrow(new AccountNotActive("Your account is blacklisted")).when(accountStatusService).requireActive(CLIENT);

        assertEquals("Your account is blacklisted",
                assertThrows(AccountNotActive.class, () -> service.bookSlot(CLIENT, SLOT, SERVICE)).getMessage());

        verifyNoInteractions(slotRepository, advisorClient, serviceTypeRepository, appointmentRepository, transactionManager, notifications, audit);
    }

    @Test
    void anUnknownSlotIs404WithoutAskingTheUserService() {
        when(slotRepository.findAdvisorIdById(SLOT)).thenReturn(Optional.empty());

        assertEquals("Slot not found", assertThrows(InformationNotFound.class, () -> service.bookSlot(CLIENT, SLOT, SERVICE)).getMessage());

        verifyNoInteractions(advisorClient, transactionManager);
    }

    @Test
    void aUserServiceThatIsDownRefusesTheBookingBeforeAnyTransactionOpens() {
        when(slotRepository.findAdvisorIdById(SLOT)).thenReturn(Optional.of(ADVISOR));
        when(advisorClient.getById(ADVISOR)).thenThrow(new ServiceUnavailable("The user service is not available, please try again later"));

        assertThrows(ServiceUnavailable.class, () -> service.bookSlot(CLIENT, SLOT, SERVICE));

        verifyNoInteractions(transactionManager, notifications, audit);
    }

    @Test
    void aSlotThatIsAlreadyTakenIsRefused() {
        bookable(slot(LocalDateTime.now().plusDays(1), SlotStatus.BOOKED), freeService(), advisor(Specialty.GENERAL, true));

        assertEquals("This slot is no longer available",
                assertThrows(SlotUnavailable.class, () -> service.bookSlot(CLIENT, SLOT, SERVICE)).getMessage());

        verify(appointmentRepository, never()).save(any());
        verifyNoInteractions(notifications, audit);
    }

    @Test
    void anInactiveAdvisorIsRefused() {
        bookable(slot(LocalDateTime.now().plusDays(1), SlotStatus.AVAILABLE), freeService(), advisor(Specialty.GENERAL, false));

        assertEquals("This advisor is no longer available",
                assertThrows(InvalidRequest.class, () -> service.bookSlot(CLIENT, SLOT, SERVICE)).getMessage());
    }

    @Test
    void anUnknownServiceTypeIs404() {
        bookable(slot(LocalDateTime.now().plusDays(1), SlotStatus.AVAILABLE), freeService(), advisor(Specialty.GENERAL, true));
        when(serviceTypeRepository.findById(SERVICE)).thenReturn(Optional.empty());

        assertEquals("Service type not found", assertThrows(InformationNotFound.class, () -> service.bookSlot(CLIENT, SLOT, SERVICE)).getMessage());
    }

    @Test
    void aServiceTypeThatIsNotActiveIsRefused() {
        AvailabilitySlot slot = slot(LocalDateTime.now().plusDays(1), SlotStatus.AVAILABLE);
        bookable(slot, serviceType(false, null, null, false), advisor(Specialty.GENERAL, true));

        assertThrows(InvalidRequest.class, () -> service.bookSlot(CLIENT, SLOT, SERVICE));

        assertEquals(SlotStatus.AVAILABLE, slot.getStatus());
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void theWrongSpecialtyIsRefusedButAGeneralAdvisorTakesAnything() {
        AvailabilitySlot slot = slot(LocalDateTime.now().plusDays(1), SlotStatus.AVAILABLE);
        ServiceType mortgages = serviceType(false, null, Specialty.MORTGAGES, true);

        bookable(slot, mortgages, advisor(Specialty.LOANS, true));
        assertEquals("This advisor does not offer the selected service",
                assertThrows(InvalidRequest.class, () -> service.bookSlot(CLIENT, SLOT, SERVICE)).getMessage());
        assertEquals(SlotStatus.AVAILABLE, slot.getStatus());

        bookable(slot, mortgages, advisor(Specialty.GENERAL, true));
        assertDoesNotThrow(() -> service.bookSlot(CLIENT, SLOT, SERVICE));
    }

    @Test
    void theMatchingSpecialtyIsAccepted() {
        bookable(slot(LocalDateTime.now().plusDays(1), SlotStatus.AVAILABLE),
                serviceType(false, null, Specialty.MORTGAGES, true), advisor(Specialty.MORTGAGES, true));

        assertDoesNotThrow(() -> service.bookSlot(CLIENT, SLOT, SERVICE));
    }

    @Test
    void aServiceThatNeedsKycIsRefusedWhenTheCheckSaysNo() {
        AvailabilitySlot slot = slot(LocalDateTime.now().plusDays(1), SlotStatus.AVAILABLE);
        bookable(slot, serviceType(true, null, null, true), advisor(Specialty.GENERAL, true));
        when(kycCheck.isApproved(CLIENT, null)).thenReturn(false);

        assertEquals("This service requires an approved KYC document",
                assertThrows(InvalidRequest.class, () -> service.bookSlot(CLIENT, SLOT, SERVICE)).getMessage());

        assertEquals(SlotStatus.AVAILABLE, slot.getStatus());
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void aSpecificDocumentTypeIsAskedForAndNamedInTheError() {
        bookable(slot(LocalDateTime.now().plusDays(1), SlotStatus.AVAILABLE),
                serviceType(true, DocumentType.PASSPORT, null, true), advisor(Specialty.GENERAL, true));
        when(kycCheck.isApproved(CLIENT, DocumentType.PASSPORT)).thenReturn(false);

        assertEquals("This service requires an approved PASSPORT document",
                assertThrows(InvalidRequest.class, () -> service.bookSlot(CLIENT, SLOT, SERVICE)).getMessage());
    }

    @Test
    void anApprovedKycLetsTheBookingThrough() {
        bookable(slot(LocalDateTime.now().plusDays(1), SlotStatus.AVAILABLE),
                serviceType(true, DocumentType.PASSPORT, null, true), advisor(Specialty.GENERAL, true));
        when(kycCheck.isApproved(CLIENT, DocumentType.PASSPORT)).thenReturn(true);

        assertEquals(AppointmentStatus.PENDING, service.bookSlot(CLIENT, SLOT, SERVICE).getStatus());
    }

    @Test
    void theUniqueIndexOnActiveAppointmentsIsA409AndNothingIsAnnounced() {
        bookable(slot(LocalDateTime.now().plusDays(1), SlotStatus.AVAILABLE), freeService(), advisor(Specialty.GENERAL, true));
        doThrow(new DataIntegrityViolationException("ux_appointments_one_active_per_slot")).when(appointmentRepository).save(any(Appointment.class));

        assertThrows(SlotUnavailable.class, () -> service.bookSlot(CLIENT, SLOT, SERVICE));

        verifyNoInteractions(notifications, audit);
    }

    @Test
    void theUserServiceIsAskedBeforeTheTransactionAndTheStubsAfterItCommits() {
        bookable(slot(LocalDateTime.now().plusDays(1), SlotStatus.AVAILABLE), freeService(), advisor(Specialty.GENERAL, true));

        service.bookSlot(CLIENT, SLOT, SERVICE);

        InOrder order = inOrder(accountStatusService, advisorClient, transactionManager, userClient, notifications, audit);
        order.verify(accountStatusService).requireActive(CLIENT);
        order.verify(advisorClient).getById(ADVISOR);
        order.verify(transactionManager).getTransaction(any());
        order.verify(transactionManager).commit(any());
        order.verify(userClient).names(any());
        order.verify(notifications).publish(anyLong(), any(), anyString(), anyLong());
        order.verify(audit).record(any(), anyLong(), anyLong(), anyString());
    }

    @Test
    void aFailingNotificationOrNameLookupDoesNotUndoTheBooking() {
        bookable(slot(LocalDateTime.now().plusDays(1), SlotStatus.AVAILABLE), freeService(), advisor(Specialty.GENERAL, true));
        when(userClient.names(any())).thenThrow(new ServiceUnavailable("down"));
        doThrow(new RuntimeException("boom")).when(notifications).publish(anyLong(), any(), anyString(), anyLong());

        Appointment result = assertDoesNotThrow(() -> service.bookSlot(CLIENT, SLOT, SERVICE));

        assertNotNull(result.getId());
        verify(audit).record(eq(AuditAction.APPOINTMENT_BOOKED), eq(CLIENT), eq(APPOINTMENT), anyString());
    }

    // confirm

    @Test
    void theOwningAdvisorConfirmsAPendingAppointment() {
        Appointment appointment = existing(AppointmentStatus.PENDING, bookedSlotStartingIn(48));
        when(advisorClient.getByUserId(ADVISOR_USER)).thenReturn(advisor(Specialty.GENERAL, true));

        Appointment result = service.confirmAppointment(ADVISOR_USER, APPOINTMENT);

        assertEquals(AppointmentStatus.CONFIRMED, result.getStatus());
        assertEquals(AppointmentStatus.CONFIRMED, appointment.getStatus());
        verify(notifications).publish(eq(CLIENT), eq(NotificationType.APPOINTMENT_CONFIRMED), contains("Advisor Name confirmed"), eq(APPOINTMENT));
        verify(audit).record(eq(AuditAction.APPOINTMENT_CONFIRMED), eq(ADVISOR_USER), eq(APPOINTMENT), contains("Advisor Name confirmed"));
    }

    @Test
    void anotherAdvisorCannotConfirm() {
        Appointment appointment = existing(AppointmentStatus.PENDING, bookedSlotStartingIn(48));
        appointment.setAdvisorId(2L);
        when(advisorClient.getByUserId(ADVISOR_USER)).thenReturn(advisor(Specialty.GENERAL, true));

        assertEquals("You can only confirm your own appointments",
                assertThrows(InvalidRequest.class, () -> service.confirmAppointment(ADVISOR_USER, APPOINTMENT)).getMessage());

        assertEquals(AppointmentStatus.PENDING, appointment.getStatus());
        verifyNoInteractions(notifications, audit);
    }

    @Test
    void onlyAPendingAppointmentCanBeConfirmed() {
        existing(AppointmentStatus.CONFIRMED, bookedSlotStartingIn(48));
        when(advisorClient.getByUserId(ADVISOR_USER)).thenReturn(advisor(Specialty.GENERAL, true));

        assertEquals("Only pending appointments can be confirmed",
                assertThrows(InvalidRequest.class, () -> service.confirmAppointment(ADVISOR_USER, APPOINTMENT)).getMessage());
    }

    @Test
    void confirmingAnUnknownAppointmentIs404() {
        when(advisorClient.getByUserId(ADVISOR_USER)).thenReturn(advisor(Specialty.GENERAL, true));
        when(appointmentRepository.findById(APPOINTMENT)).thenReturn(Optional.empty());

        assertThrows(InformationNotFound.class, () -> service.confirmAppointment(ADVISOR_USER, APPOINTMENT));
    }

    @Test
    void aCallerWithoutAnAdvisorProfileIs404AndNoTransactionOpens() {
        when(advisorClient.getByUserId(ADVISOR_USER)).thenThrow(new InformationNotFound("Advisor profile not found"));

        assertThrows(InformationNotFound.class, () -> service.confirmAppointment(ADVISOR_USER, APPOINTMENT));

        verifyNoInteractions(transactionManager, appointmentRepository);
    }

    @Test
    void confirmingChecksTheAccountFirst() {
        doThrow(new AccountNotActive("Your account is not active")).when(accountStatusService).requireActive(ADVISOR_USER);

        assertThrows(AccountNotActive.class, () -> service.confirmAppointment(ADVISOR_USER, APPOINTMENT));

        verifyNoInteractions(advisorClient, appointmentRepository, transactionManager);
    }

    @Test
    void aVersionConflictOnConfirmIsPassedOnAndNothingIsAnnounced() {
        existing(AppointmentStatus.PENDING, bookedSlotStartingIn(48));
        when(advisorClient.getByUserId(ADVISOR_USER)).thenReturn(advisor(Specialty.GENERAL, true));
        doThrow(new ObjectOptimisticLockingFailureException(Appointment.class, APPOINTMENT)).when(appointmentRepository).save(any(Appointment.class));

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> service.confirmAppointment(ADVISOR_USER, APPOINTMENT));

        verifyNoInteractions(notifications, audit);
    }

    // cancel

    @Test
    void aClientCancellingInsideTwentyFourHoursGetsAStrikeAndAnImminentSlotStaysBooked() {
        AvailabilitySlot slot = bookedSlotStartingIn(2);
        Appointment appointment = existing(AppointmentStatus.CONFIRMED, slot);
        when(strikeClient.addStrike(CLIENT)).thenReturn(new StrikeResult(1, false));

        service.cancelAppointment(APPOINTMENT, CLIENT, null);

        verify(strikeClient).addStrike(CLIENT);
        assertEquals(AppointmentStatus.CANCELLED, appointment.getStatus());
        assertNotNull(appointment.getCancelledAt());
        assertEquals("Cancelled by Client less than the accepted cancellation window", appointment.getCancellationReason());
        assertEquals(SlotStatus.BOOKED, slot.getStatus()); // less than 6 hours left, nobody else can book it
        verify(notifications).publish(eq(CLIENT), eq(NotificationType.APPOINTMENT_NO_SHOW), contains("Strikes: 1/3"), eq(APPOINTMENT));
    }

    @Test
    void aLateCancellationWithEnoughNoticeStillGetsAStrikeButReleasesTheSlot() {
        AvailabilitySlot slot = bookedSlotStartingIn(10);
        existing(AppointmentStatus.CONFIRMED, slot);
        when(strikeClient.addStrike(CLIENT)).thenReturn(new StrikeResult(1, false));

        service.cancelAppointment(APPOINTMENT, CLIENT, null);

        verify(strikeClient).addStrike(CLIENT);
        assertEquals(SlotStatus.AVAILABLE, slot.getStatus());
        verify(slotRepository).save(slot);
    }

    @Test
    void cancellingMoreThanTwentyFourHoursAheadHasNoStrikeAndReleasesTheSlot() {
        AvailabilitySlot slot = bookedSlotStartingIn(72);
        Appointment appointment = existing(AppointmentStatus.CONFIRMED, slot);

        service.cancelAppointment(APPOINTMENT, CLIENT, null);

        verifyNoInteractions(strikeClient);
        assertEquals(SlotStatus.AVAILABLE, slot.getStatus());
        assertEquals("Cancelled by Client", appointment.getCancellationReason());
        verify(audit).record(eq(AuditAction.APPOINTMENT_CANCELLED), eq(CLIENT), eq(APPOINTMENT), eq("Cancelled by Client"));
    }

    @Test
    void aBlacklistingStrikeIsAuditedAndAPlainOneIsNot() {
        existing(AppointmentStatus.CONFIRMED, bookedSlotStartingIn(1));
        when(strikeClient.addStrike(CLIENT)).thenReturn(new StrikeResult(3, true));

        service.cancelAppointment(APPOINTMENT, CLIENT, null);

        verify(audit).record(eq(AuditAction.CLIENT_BLACKLISTED), eq(CLIENT), eq(CLIENT), contains("3 strikes"));
    }

    @Test
    void aStrikeThatDoesNotBlacklistWritesNoBlacklistAudit() {
        existing(AppointmentStatus.CONFIRMED, bookedSlotStartingIn(1));
        when(strikeClient.addStrike(CLIENT)).thenReturn(new StrikeResult(2, false));

        service.cancelAppointment(APPOINTMENT, CLIENT, null);

        verify(audit, never()).record(eq(AuditAction.CLIENT_BLACKLISTED), anyLong(), anyLong(), anyString());
    }

    @Test
    void theReasonGivenIsTrimmedAndAddedToTheLateReason() {
        Appointment late = existing(AppointmentStatus.CONFIRMED, bookedSlotStartingIn(3));
        when(strikeClient.addStrike(CLIENT)).thenReturn(new StrikeResult(1, false));

        service.cancelAppointment(APPOINTMENT, CLIENT, "  I am ill  ");

        assertEquals("Cancelled by Client less than the accepted cancellation window, reason given: I am ill", late.getCancellationReason());
    }

    @Test
    void aBlankReasonIsTreatedAsNoReason() {
        Appointment early = existing(AppointmentStatus.CONFIRMED, bookedSlotStartingIn(72));

        service.cancelAppointment(APPOINTMENT, CLIENT, "   ");

        assertEquals("Cancelled by Client", early.getCancellationReason());
    }

    @Test
    void anAdvisorCancellingNeverGetsTheClientAStrikeAndAlwaysFreesTheSlot() {
        AvailabilitySlot slot = bookedSlotStartingIn(1);
        Appointment appointment = existing(AppointmentStatus.CONFIRMED, slot);

        service.cancelAppointment(APPOINTMENT, ADVISOR_USER, null);

        verifyNoInteractions(strikeClient);
        assertEquals(SlotStatus.AVAILABLE, slot.getStatus());
        assertEquals("Cancelled by Advisor", appointment.getCancellationReason());
        verify(notifications).publish(eq(CLIENT), eq(NotificationType.APPOINTMENT_CANCELLED), contains("Reason: Cancelled by Advisor"), eq(APPOINTMENT));
    }

    @Test
    void aClientCancellingNotifiesTheAdvisor() {
        existing(AppointmentStatus.PENDING, bookedSlotStartingIn(72));

        service.cancelAppointment(APPOINTMENT, CLIENT, null);

        verify(notifications).publish(eq(ADVISOR_USER), eq(NotificationType.APPOINTMENT_CANCELLED), contains("cancelled the appointment"), eq(APPOINTMENT));
    }

    @Test
    void someoneWhoIsNeitherTheClientNorTheAdvisorCannotCancel() {
        AvailabilitySlot slot = bookedSlotStartingIn(1);
        Appointment appointment = existing(AppointmentStatus.CONFIRMED, slot);

        assertEquals("You are not authorized to cancel this appointment",
                assertThrows(InvalidRequest.class, () -> service.cancelAppointment(APPOINTMENT, 999L, null)).getMessage());

        assertEquals(AppointmentStatus.CONFIRMED, appointment.getStatus());
        assertEquals(SlotStatus.BOOKED, slot.getStatus());
        verifyNoInteractions(strikeClient, notifications, audit);
    }

    @Test
    void anAppointmentThatIsAlreadyOverCannotBeCancelled() {
        for (AppointmentStatus over : List.of(AppointmentStatus.COMPLETED, AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW)) {
            existing(over, slot(LocalDateTime.now().minusDays(1), SlotStatus.BOOKED));

            assertEquals("Only pending or confirmed appointments can be cancelled",
                    assertThrows(InvalidRequest.class, () -> service.cancelAppointment(APPOINTMENT, CLIENT, null)).getMessage(), over.name());
        }
        verifyNoInteractions(strikeClient);
    }

    @Test
    void cancellingAnUnknownAppointmentIs404() {
        when(appointmentRepository.findAdvisorIdById(APPOINTMENT)).thenReturn(Optional.empty());

        assertEquals("Appointment not found", assertThrows(InformationNotFound.class, () -> service.cancelAppointment(APPOINTMENT, CLIENT, null)).getMessage());

        verifyNoInteractions(advisorClient, transactionManager);
    }

    @Test
    void cancellingChecksTheAccountFirst() {
        doThrow(new AccountNotActive("Your account is blacklisted")).when(accountStatusService).requireActive(CLIENT);

        assertThrows(AccountNotActive.class, () -> service.cancelAppointment(APPOINTMENT, CLIENT, null));

        verifyNoInteractions(appointmentRepository, advisorClient, transactionManager, strikeClient);
    }

    // strikes happen after the commit and cannot undo the cancellation

    @Test
    void theStrikeCallComesAfterTheTransactionCommits() {
        existing(AppointmentStatus.CONFIRMED, bookedSlotStartingIn(2));
        when(strikeClient.addStrike(CLIENT)).thenReturn(new StrikeResult(1, false));

        service.cancelAppointment(APPOINTMENT, CLIENT, null);

        InOrder order = inOrder(accountStatusService, advisorClient, transactionManager, strikeClient, audit, notifications);
        order.verify(accountStatusService).requireActive(CLIENT);
        order.verify(advisorClient).getById(ADVISOR);
        order.verify(transactionManager).getTransaction(any());
        order.verify(transactionManager).commit(any());
        order.verify(strikeClient).addStrike(CLIENT);
        order.verify(audit).record(eq(AuditAction.APPOINTMENT_CANCELLED), anyLong(), anyLong(), anyString());
    }

    @Test
    void aFailedStrikeCallKeepsTheCancellationAndStillAnnouncesIt() {
        AvailabilitySlot slot = bookedSlotStartingIn(10);
        Appointment appointment = existing(AppointmentStatus.CONFIRMED, slot);
        when(strikeClient.addStrike(CLIENT)).thenThrow(new ServiceUnavailable("The user service is not available, please try again later"));

        assertDoesNotThrow(() -> service.cancelAppointment(APPOINTMENT, CLIENT, null));

        assertEquals(AppointmentStatus.CANCELLED, appointment.getStatus());
        assertEquals(SlotStatus.AVAILABLE, slot.getStatus());
        verify(transactionManager, never()).rollback(any());
        verify(transactionManager).commit(any());
        verify(audit).record(eq(AuditAction.APPOINTMENT_CANCELLED), eq(CLIENT), eq(APPOINTMENT), anyString());
        verify(notifications).publish(eq(ADVISOR_USER), eq(NotificationType.APPOINTMENT_CANCELLED), anyString(), eq(APPOINTMENT));
        verify(audit, never()).record(eq(AuditAction.CLIENT_BLACKLISTED), anyLong(), anyLong(), anyString());
    }

    @Test
    void aCancellationThatFailsInsideTheTransactionCallsNoStrike() {
        existing(AppointmentStatus.CONFIRMED, bookedSlotStartingIn(2));
        doThrow(new ObjectOptimisticLockingFailureException(Appointment.class, APPOINTMENT)).when(appointmentRepository).save(any(Appointment.class));

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> service.cancelAppointment(APPOINTMENT, CLIENT, null));

        verifyNoInteractions(strikeClient, notifications, audit);
    }
}
