package com.example.bookingservice.repository;

import com.example.bookingservice.model.Appointment;
import com.example.bookingservice.model.enums.AppointmentStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findByAdvisorIdAndStatusIn(Long advisorId, List<AppointmentStatus> statuses);
    List<Appointment> findByClientIdAndStatusIn(Long clientId, List<AppointmentStatus> statuses);
    List<Appointment> findByClientId(Long clientId);
    List<Appointment> findByAdvisorId(Long advisorId);
    List<Appointment> findByClientId(Long clientId, Sort sort);
    List<Appointment> findByClientIdAndStatus(Long clientId, AppointmentStatus status, Sort sort);
    List<Appointment> findByAdvisorId(Long advisorId, Sort sort);
    List<Appointment> findByAdvisorIdAndStatus(Long advisorId, AppointmentStatus status, Sort sort);
    List<Appointment> findByStatus(AppointmentStatus status, Sort sort);
    List<Appointment> findByStatusAndSlot_EndTimeBefore(AppointmentStatus status, LocalDateTime cutoff);
}
