package com.example.finConsult.service;

import com.example.finConsult.exception.InformationNotFound;
import com.example.finConsult.exception.InvalidRequest;
import com.example.finConsult.model.Advisor;
import com.example.finConsult.model.Appointment;
import com.example.finConsult.model.AvailabilitySlot;
import com.example.finConsult.model.User;
import com.example.finConsult.model.enums.AppointmentStatus;
import com.example.finConsult.model.enums.SlotStatus;
import com.example.finConsult.repository.AdvisorRepository;
import com.example.finConsult.repository.AppointmentRepository;
import com.example.finConsult.repository.AvailabilitySlotRepository;
import com.example.finConsult.repository.NotificationRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final NotificationRepository notificationRepository;
    private final AdvisorRepository advisorRepository;

    @Transactional
    public Appointment bookSlot(User client, Long slotId) {
        AvailabilitySlot slot = availabilitySlotRepository.findById(slotId)
                .orElseThrow(() -> new InformationNotFound("Slot not found"));

        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            throw new InvalidRequest("This slot is no longer available");
        }

        Appointment appointment = new Appointment();
        appointment.setClient(client);
        appointment.setAdvisor(slot.getAdvisor());
        appointment.setSlot(slot);
        appointment.setStatus(AppointmentStatus.PENDING);

        slot.setStatus(SlotStatus.BOOKED);
        availabilitySlotRepository.save(slot);

        return appointmentRepository.save(appointment);
    }

    // advisor agrees to the booked slot (Gets notified)
    @Transactional
    public Appointment confirmAppointment(Long advisorId, Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new InformationNotFound("Appointment not found"));

        if (!appointment.getAdvisor().getId().equals(advisorId)) {
            throw new InvalidRequest("You can only confirm your own appointments");
        }

        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new InvalidRequest("Only pending appointments can be confirmed");
        }

        appointment.setStatus(AppointmentStatus.CONFIRMED);
        return appointmentRepository.save(appointment);
    }

    @Transactional
    public void cancelAppointment(Long appointmentId, String reason, User cancelledBy) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new InformationNotFound("Appointment not found"));

        boolean isClient = appointment.getClient().getId().equals(cancelledBy.getId());
        boolean isAdvisor = appointment.getAdvisor().getUser().getId().equals(cancelledBy.getId());

        //confirm either the proper client or the advisor is the one who cancelled
        if (!isClient && !isAdvisor) {
            throw new InvalidRequest("You are not authorized to cancel this appointment");
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancellationReason(reason);
        appointment.setCancelledAt(LocalDateTime.now());
        appointmentRepository.save(appointment);

        AvailabilitySlot slot = appointment.getSlot();
        slot.setStatus(SlotStatus.AVAILABLE);
        availabilitySlotRepository.save(slot);

        // notification for both advisor and client that the appointment has been cancelled (TODO)
    }

    // List appointments for a client
    public List<Appointment> getClientAppointments(User client) {
        return appointmentRepository.findByClient(client);
    }

    // List appointments for an advisor
    public List<Appointment> getAdvisorAppointments(Long advisorId) {
        Advisor advisor = advisorRepository.findById(advisorId)
                .orElseThrow(() -> new InformationNotFound("Advisor not found"));

        return appointmentRepository.findByAdvisor(advisor);
    }
}