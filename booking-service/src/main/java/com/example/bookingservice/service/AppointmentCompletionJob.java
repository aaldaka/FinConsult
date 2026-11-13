package com.example.bookingservice.service;

import com.example.bookingservice.model.Appointment;
import com.example.bookingservice.model.enums.AppointmentStatus;
import com.example.bookingservice.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// closes out confirmed appointments whose slot has finished. Only CONFIRMED ones are touched: a no-show has to be marked
// while the appointment is still confirmed, so anything completed here can no longer be marked. Running it twice changes
// nothing the second time, so it is harmless if the monolith's job is also running
@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentCompletionJob {

    private final AppointmentRepository appointmentRepository;

    @Scheduled(fixedRate = 15 * 60 * 1000L) // every 15 minutes
    @Transactional
    public void completeFinishedAppointments() {
        List<Appointment> finished = appointmentRepository
                .findByStatusAndSlot_EndTimeBefore(AppointmentStatus.CONFIRMED, LocalDateTime.now());

        if (finished.isEmpty()) {
            return;
        }

        for (Appointment appointment : finished) {
            appointment.setStatus(AppointmentStatus.COMPLETED);
            appointmentRepository.save(appointment);
        }

        log.info("Marked {} finished appointment(s) as completed", finished.size());
    }
}
