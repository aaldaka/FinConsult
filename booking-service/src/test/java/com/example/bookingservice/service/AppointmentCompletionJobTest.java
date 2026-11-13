package com.example.bookingservice.service;

import com.example.bookingservice.model.Appointment;
import com.example.bookingservice.model.AvailabilitySlot;
import com.example.bookingservice.model.enums.AppointmentStatus;
import com.example.bookingservice.repository.AppointmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// no Spring and no database, the repository is a mock
@ExtendWith(MockitoExtension.class)
class AppointmentCompletionJobTest {

    @Mock AppointmentRepository appointmentRepository;
    @InjectMocks AppointmentCompletionJob job;

    private Appointment confirmed(long id) {
        AvailabilitySlot slot = new AvailabilitySlot();
        slot.setEndTime(LocalDateTime.now().minusMinutes(10));
        Appointment appointment = new Appointment();
        appointment.setId(id);
        appointment.setSlot(slot);
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        return appointment;
    }

    @Test
    void confirmedAppointmentsWhoseSlotHasEndedAreCompletedAndSaved() {
        Appointment first = confirmed(1);
        Appointment second = confirmed(2);
        when(appointmentRepository.findByStatusAndSlot_EndTimeBefore(eq(AppointmentStatus.CONFIRMED), any(LocalDateTime.class)))
                .thenReturn(List.of(first, second));

        job.completeFinishedAppointments();

        assertEquals(AppointmentStatus.COMPLETED, first.getStatus());
        assertEquals(AppointmentStatus.COMPLETED, second.getStatus());
        verify(appointmentRepository).save(first);
        verify(appointmentRepository).save(second);
    }

    @Test
    void onlyConfirmedAppointmentsAreAskedForAndTheCutoffIsNow() {
        when(appointmentRepository.findByStatusAndSlot_EndTimeBefore(any(), any())).thenReturn(List.of());
        LocalDateTime before = LocalDateTime.now();

        job.completeFinishedAppointments();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(appointmentRepository).findByStatusAndSlot_EndTimeBefore(eq(AppointmentStatus.CONFIRMED), cutoff.capture());
        assertFalse(cutoff.getValue().isBefore(before));
        assertTrue(cutoff.getValue().isBefore(LocalDateTime.now().plusSeconds(1)));
    }

    @Test
    void nothingFinishedMeansNothingIsSaved() {
        when(appointmentRepository.findByStatusAndSlot_EndTimeBefore(any(), any())).thenReturn(List.of());

        job.completeFinishedAppointments();

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void runningTwiceDoesNotCompleteAnythingAgain() {
        Appointment appointment = confirmed(1);
        when(appointmentRepository.findByStatusAndSlot_EndTimeBefore(any(), any()))
                .thenReturn(List.of(appointment))
                .thenReturn(List.of());

        job.completeFinishedAppointments();
        job.completeFinishedAppointments();

        assertEquals(AppointmentStatus.COMPLETED, appointment.getStatus());
        verify(appointmentRepository, times(1)).save(any());
    }
}
