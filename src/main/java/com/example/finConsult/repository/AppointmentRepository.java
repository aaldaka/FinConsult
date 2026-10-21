package com.example.finConsult.repository;

import com.example.finConsult.model.Advisor;
import com.example.finConsult.model.Appointment;
import com.example.finConsult.model.User;
import com.example.finConsult.model.enums.AppointmentStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findByAdvisorAndStatusIn(Advisor advisor, List<AppointmentStatus> statuses);
    List<Appointment> findByClientAndStatusIn(User user, List<AppointmentStatus> statuses);
    List<Appointment> findByClient(User client);
    List<Appointment> findByAdvisor(Advisor advisor);
    List<Appointment> findByClient(User client, Sort sort);
    List<Appointment> findByClientAndStatus(User client, AppointmentStatus status, Sort sort);
    List<Appointment> findByAdvisor(Advisor advisor, Sort sort);
    List<Appointment> findByAdvisorAndStatus(Advisor advisor, AppointmentStatus status, Sort sort);
    List<Appointment> findByStatusAndSlot_EndTimeBefore(AppointmentStatus status, LocalDateTime cutoff);
}