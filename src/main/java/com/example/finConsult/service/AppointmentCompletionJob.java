package com.example.finConsult.service;

import com.example.finConsult.model.Appointment;
import com.example.finConsult.model.enums.AppointmentStatus;
import com.example.finConsult.repository.AppointmentRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/** Every 15 minutes, marks confirmed appointments whose slot has ended as COMPLETED. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentCompletionJob {

    private final AppointmentRepository appointmentRepository;

    /** Marks CONFIRMED appointments whose slot has ended as COMPLETED. */
    @Scheduled(fixedRate = 15 * 60 * 1000L) // 15 mins
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
