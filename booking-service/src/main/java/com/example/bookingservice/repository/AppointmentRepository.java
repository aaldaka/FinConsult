package com.example.bookingservice.repository;

import com.example.bookingservice.model.Appointment;
import com.example.bookingservice.model.enums.AppointmentStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    // a plain value, so no appointment entity (and version) is left in the persistence context before the transaction
    @Query("select a.advisorId from Appointment a where a.id = :id")
    Optional<Long> findAdvisorIdById(@Param("id") Long id);

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
