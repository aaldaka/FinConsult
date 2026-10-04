package com.example.finConsult.service;

import com.example.finConsult.exception.InformationIncorrect;
import com.example.finConsult.exception.InformationNotFound;
import com.example.finConsult.exception.InvalidRequest;
import com.example.finConsult.model.*;
import com.example.finConsult.model.enums.AppointmentStatus;
import com.example.finConsult.model.enums.NotificationType;
import com.example.finConsult.model.enums.SlotStatus;
import com.example.finConsult.repository.*;
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
    private final ServiceTypeRepository serviceTypeRepository;
    private final UserRepository userRepository;

    @Transactional
    public Appointment bookSlot(User client, Long slotId, Long serviceId) {
        AvailabilitySlot slot = availabilitySlotRepository.findById(slotId)
                .orElseThrow(() -> new InformationNotFound("Slot not found"));

        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            throw new InvalidRequest("This slot is no longer available");
        }

        ServiceType serviceType = serviceTypeRepository.findById(serviceId)
                .orElseThrow(() -> new InformationNotFound("Service type not found"));

        Appointment appointment = new Appointment();
        appointment.setClient(client);
        appointment.setAdvisor(slot.getAdvisor());
        appointment.setSlot(slot);
        appointment.setServiceType(serviceType);
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
        User whoToNotify = isClient ? appointment.getAdvisor().getUser() : appointment.getClient(); //notify the other party of the cancellation
        Notification notif = new Notification();
        notif.setUser(whoToNotify);
        notif.setRelatedAppointment(appointment);
        notif.setType(NotificationType.APPOINTMENT_CANCELLED);
        notif.setMessage("The appointment with ID: " + appointmentId + " has been cancelled due to: "+ reason);
        notificationRepository.save(notif);

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

    @Transactional
    public Appointment markNoShow(Long advisorId, Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new InformationNotFound("Appointment not found"));

        if (!appointment.getAdvisor().getId().equals(advisorId)) {
            throw new InvalidRequest("You can only mark your own appointments");
        }

        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new InvalidRequest("Only confirmed appointments can be marked as no-show");
        }
// optional (unmark for testing)
        if (appointment.getSlot().getStartTime().plusMinutes(20).isAfter(LocalDateTime.now())) {
            throw new InvalidRequest("Cannot mark no-show until 15 minutes after the appointment start time");
        }

        appointment.setStatus(AppointmentStatus.NO_SHOW);
        appointmentRepository.save(appointment);

        User client = appointment.getClient();
        client.setNoShowStrikes(client.getNoShowStrikes()+ 1);

        if (client.getNoShowStrikes() >= 3) {
            client.setBlacklisted(true);
        }

        userRepository.save(client);
        return appointment;

    }

    @Transactional
    public Appointment excuseNoShow(Long appointmentId, String reason) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new InformationNotFound("Appointment not found"));

        if (!appointment.getStatus().equals(AppointmentStatus.NO_SHOW)){
            throw new InvalidRequest("Appointment was not marked as a No-Show");
        }

        User client = appointment.getClient();
        appointment.setExcused(true);
        appointment.setExcuseReason(reason);
        client.setNoShowStrikes(client.getNoShowStrikes()>0? client.getNoShowStrikes() - 1 : 0 );
        client.setBlacklisted(client.getNoShowStrikes() >=3);

        userRepository.save(client);
        return appointmentRepository.save(appointment);
    }
}