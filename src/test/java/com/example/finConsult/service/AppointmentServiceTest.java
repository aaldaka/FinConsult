package com.example.finConsult.service;

import com.example.finConsult.exception.InvalidRequest;
import com.example.finConsult.exception.SlotUnavailable;
import com.example.finConsult.model.*;
import com.example.finConsult.model.enums.*;
import com.example.finConsult.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock AppointmentRepository appointmentRepository;
    @Mock AvailabilitySlotRepository availabilitySlotRepository;
    @Mock AdvisorRepository advisorRepository;
    @Mock ServiceTypeRepository serviceTypeRepository;
    @Mock UserRepository userRepository;
    @Mock AuditLogService auditLogService;
    @Mock KycDocumentRepository kycDocumentRepository;
    @Mock NotificationService notificationService;


    @InjectMocks AppointmentService appointmentService;

    private User newUser(long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    private Advisor newAdvisor(long id, Specialty specialty) {
        Advisor a = new Advisor();
        a.setId(id);
        a.setSpecialty(specialty);
        a.setActive(true);
        a.setUser(newUser(id + 100));
        return a;
    }

    private AvailabilitySlot newSlot(Advisor advisor, LocalDateTime start) {
        AvailabilitySlot s = new AvailabilitySlot();
        s.setId(1L);
        s.setAdvisor(advisor);
        s.setStartTime(start);
        s.setEndTime(start.plusHours(1));
        s.setStatus(SlotStatus.AVAILABLE);
        return s;
    }

    @Test
    void bookSlotOnAvailableSlotWorks() {
        Advisor advisor = newAdvisor(1, Specialty.GENERAL);
        AvailabilitySlot slot = newSlot(advisor, LocalDateTime.now().plusDays(1));
        ServiceType service = new ServiceType();
        service.setId(5L);
        service.setRequiresKyc(false);
        User client = newUser(10);

        when(availabilitySlotRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(slot));
        when(serviceTypeRepository.findById(5L)).thenReturn(Optional.of(service));
        when(appointmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Appointment result = appointmentService.bookSlot(client, 1L, 5L);

        assertEquals(AppointmentStatus.PENDING, result.getStatus());
        assertEquals(SlotStatus.BOOKED, slot.getStatus());
    }

    @Test
    void bookingTakenSlotFails() {
        Advisor advisor = newAdvisor(1, Specialty.GENERAL);
        AvailabilitySlot slot = newSlot(advisor, LocalDateTime.now().plusDays(1));
        slot.setStatus(SlotStatus.BOOKED);
        when(availabilitySlotRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(slot));

        User client = newUser(10);
        assertThrows(SlotUnavailable.class, () -> appointmentService.bookSlot(client, 1L, 5L));
    }

    @Test
    void bookingWrongSpecialtyFails() {
        Advisor advisor = newAdvisor(1, Specialty.LOANS);
        AvailabilitySlot slot = newSlot(advisor, LocalDateTime.now().plusDays(1));
        ServiceType service = new ServiceType();
        service.setId(5L);
        service.setRequiredSpecialty(Specialty.MORTGAGES);
        service.setRequiresKyc(false);

        when(availabilitySlotRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(slot));
        when(serviceTypeRepository.findById(5L)).thenReturn(Optional.of(service));

        User client = newUser(10);
        assertThrows(InvalidRequest.class, () -> appointmentService.bookSlot(client, 1L, 5L));
    }

    @Test
    void bookingMissingKycFails() {
        Advisor advisor = newAdvisor(1, Specialty.GENERAL);
        AvailabilitySlot slot = newSlot(advisor, LocalDateTime.now().plusDays(1));
        ServiceType service = new ServiceType();
        service.setId(5L);
        service.setRequiresKyc(true);

        when(availabilitySlotRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(slot));
        when(serviceTypeRepository.findById(5L)).thenReturn(Optional.of(service));
        when(kycDocumentRepository.existsByUserAndStatus(any(), eq(KycStatus.APPROVED))).thenReturn(false);

        User client = newUser(10);
        assertThrows(InvalidRequest.class, () -> appointmentService.bookSlot(client, 1L, 5L));
    }

    @Test
    void cancelLessThan24hAddsStrike() {
        User client = newUser(10);
        Advisor advisor = newAdvisor(1, Specialty.GENERAL);
        AvailabilitySlot slot = newSlot(advisor, LocalDateTime.now().plusHours(2));
        Appointment appt = new Appointment();
        appt.setId(1L);
        appt.setClient(client);
        appt.setAdvisor(advisor);
        appt.setSlot(slot);
        appt.setStatus(AppointmentStatus.CONFIRMED);

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));
        when(userRepository.addStrike(10L)).thenReturn(1);

        appointmentService.cancelAppointment(1L, client, null);

        verify(userRepository).addStrike(10L);
        assertEquals(AppointmentStatus.CANCELLED, appt.getStatus());
    }

    @Test
    void cancelLateWithEnoughNoticeReleasesSlot() {
        User client = newUser(10);
        Advisor advisor = newAdvisor(1, Specialty.GENERAL);
        AvailabilitySlot slot = newSlot(advisor, LocalDateTime.now().plusHours(10));
        Appointment appt = new Appointment();
        appt.setId(1L);
        appt.setClient(client);
        appt.setAdvisor(advisor);
        appt.setSlot(slot);
        appt.setStatus(AppointmentStatus.CONFIRMED);

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));
        when(userRepository.addStrike(10L)).thenReturn(1);

        appointmentService.cancelAppointment(1L, client, null);

        verify(userRepository).addStrike(10L);
        assertEquals(SlotStatus.AVAILABLE, slot.getStatus());
    }

    @Test
    void cancelTooCloseKeepsSlotBooked() {
        User client = newUser(10);
        Advisor advisor = newAdvisor(1, Specialty.GENERAL);
        AvailabilitySlot slot = newSlot(advisor, LocalDateTime.now().plusHours(2));
        slot.setStatus(SlotStatus.BOOKED);
        Appointment appt = new Appointment();
        appt.setId(1L);
        appt.setClient(client);
        appt.setAdvisor(advisor);
        appt.setSlot(slot);
        appt.setStatus(AppointmentStatus.CONFIRMED);

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));
        when(userRepository.addStrike(10L)).thenReturn(1);

        appointmentService.cancelAppointment(1L, client, null);

        verify(userRepository).addStrike(10L);
        assertEquals(SlotStatus.BOOKED, slot.getStatus());
    }

    @Test
    void cancelMoreThan24hNoStrike() {
        User client = newUser(10);
        Advisor advisor = newAdvisor(1, Specialty.GENERAL);
        AvailabilitySlot slot = newSlot(advisor, LocalDateTime.now().plusDays(3));
        Appointment appt = new Appointment();
        appt.setId(1L);
        appt.setClient(client);
        appt.setAdvisor(advisor);
        appt.setSlot(slot);
        appt.setStatus(AppointmentStatus.CONFIRMED);

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));

        appointmentService.cancelAppointment(1L, client, null);

        verify(userRepository, never()).addStrike(any());
        assertEquals(SlotStatus.AVAILABLE, slot.getStatus());
    }

    @Test
    void thirdStrikeBlacklistsClient() {
        User client = newUser(10);
        Advisor advisor = newAdvisor(1, Specialty.GENERAL);
        AvailabilitySlot slot = newSlot(advisor, LocalDateTime.now().plusHours(1));
        Appointment appt = new Appointment();
        appt.setId(1L);
        appt.setClient(client);
        appt.setAdvisor(advisor);
        appt.setSlot(slot);
        appt.setStatus(AppointmentStatus.CONFIRMED);

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));
        when(userRepository.addStrike(10L)).thenReturn(3);

        appointmentService.cancelAppointment(1L, client, null);

        verify(userRepository).addStrike(10L);
        verify(auditLogService).log(eq(client), eq(AuditAction.CLIENT_BLACKLISTED), eq("User"), eq(10L), anyString());
    }

    @Test
    void markNoShowTooSoonFails() {
        Advisor advisor = newAdvisor(1, Specialty.GENERAL);
        AvailabilitySlot slot = newSlot(advisor, LocalDateTime.now().minusMinutes(5));
        Appointment appt = new Appointment();
        appt.setId(1L);
        appt.setAdvisor(advisor);
        appt.setSlot(slot);
        appt.setStatus(AppointmentStatus.CONFIRMED);
        appt.setClient(newUser(10));

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));

        assertThrows(InvalidRequest.class, () -> appointmentService.markNoShow(1L, 1L));
    }

    @Test
    void excuseNoShowRemovesStrike() {
        User client = newUser(10);
        Appointment appt = new Appointment();
        appt.setId(1L);
        appt.setClient(client);
        appt.setStatus(AppointmentStatus.NO_SHOW);

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));
        when(appointmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.removeStrike(10L)).thenReturn(2);

        User admin = newUser(99);
        appointmentService.excuseNoShow(1L, "mistake", admin);

        verify(userRepository).removeStrike(10L);
        assertTrue(appt.isExcused());
    }

    @Test
    void excuseTwiceFails() {
        Appointment appt = new Appointment();
        appt.setId(1L);
        appt.setClient(newUser(10));
        appt.setStatus(AppointmentStatus.NO_SHOW);
        appt.setExcused(true);

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));

        assertThrows(InvalidRequest.class, () -> appointmentService.excuseNoShow(1L, "mistake", newUser(99)));
        verify(userRepository, never()).removeStrike(any());
    }

    @Test
    void cancelCompletedFails() {
        User client = newUser(10);
        Advisor advisor = newAdvisor(1, Specialty.GENERAL);
        Appointment appt = new Appointment();
        appt.setId(1L);
        appt.setClient(client);
        appt.setAdvisor(advisor);
        appt.setSlot(newSlot(advisor, LocalDateTime.now().minusDays(1)));
        appt.setStatus(AppointmentStatus.COMPLETED);

        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(appt));

        assertThrows(InvalidRequest.class, () -> appointmentService.cancelAppointment(1L, client, null));
    }
}