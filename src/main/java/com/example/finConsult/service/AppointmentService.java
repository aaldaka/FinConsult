package com.example.finConsult.service;

import com.example.finConsult.exception.InformationNotFound;
import com.example.finConsult.exception.InvalidRequest;
import com.example.finConsult.model.*;
import com.example.finConsult.model.enums.AppointmentStatus;
import com.example.finConsult.model.enums.AuditAction;
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
    private static final int CANCELLATION_STRIKE_WINDOW_HOURS = 24;

    private final AppointmentRepository appointmentRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final NotificationRepository notificationRepository;
    private final AdvisorRepository advisorRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public void cancelAppointment(Long appointmentId, User cancelledBy) {
        cancelAppointment(appointmentId, cancelledBy, null);
    }

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

        // an advisor gets notified so they can confirm their availability for the booked slot
        Notification notif = new Notification();
        notif.setUser(slot.getAdvisor().getUser());
        notif.setType(NotificationType.APPOINTMENT_BOOKED);
        notif.setMessage("Client: " + client.getId() + " has booked an appointment. Pending your confirmation.");
        notif.setRelatedAppointment(appointment);
        notificationRepository.save(notif);

        return appointment;
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

        // client gets notified of the cofirmed appointment
        Notification notif = new Notification();
        notif.setUser(appointment.getClient());
        notif.setType(NotificationType.APPOINTMENT_CONFIRMED);
        notif.setMessage("Your appointment has been confirmed!");
        notif.setRelatedAppointment(appointment);
        notificationRepository.save(notif);

        return appointmentRepository.save(appointment);
    }

    @Transactional
    public void cancelAppointment(Long appointmentId, User cancelledBy, String additionalNotes) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new InformationNotFound("Appointment not found"));

        boolean isClient = appointment.getClient().getId().equals(cancelledBy.getId());
        boolean isAdvisor = appointment.getAdvisor().getUser().getId().equals(cancelledBy.getId());

        //confirm either the proper client or the advisor is the one who cancelled
        if (!isClient && !isAdvisor) {
            throw new InvalidRequest("You are not authorized to cancel this appointment");
        }

        if (appointment.getStatus().equals(AppointmentStatus.CANCELLED) || appointment.getStatus().equals(AppointmentStatus.NO_SHOW)){
            throw new InvalidRequest("Cannot cancel an appointment that is marked as Cancelled or No Show");
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancelledAt(LocalDateTime.now());
        String reason;

        if (isAdvisor) {
            reason = additionalNotes != null ? additionalNotes : "Cancelled by Advisor";
        } // if the current time is before the appointment (minus a day) ie u met the minimum required for the cancellation
        else if (LocalDateTime.now().isBefore(appointment.getSlot().getStartTime().minusHours(CANCELLATION_STRIKE_WINDOW_HOURS))){
            reason = additionalNotes != null ? additionalNotes : "Cancelled by Client";

            } else {// if the current time is before the appointment (minus a day) ie u met the minimum required for the cancellation
            reason = "Cancelled by Client less than the accepted cancellation window"
                    + (additionalNotes != null ? " — reason given: " + additionalNotes : "");
                User client = appointment.getClient();
                client.setNoShowStrikes(client.getNoShowStrikes() + 1);

            if (client.getNoShowStrikes() >= 3) {
                client.setBlacklisted(true);
            }
            userRepository.save(client);

            Notification notif = new Notification();
            notif.setUser(client);
            notif.setRelatedAppointment(appointment);
            notif.setType(NotificationType.APPOINTMENT_NO_SHOW);
            notif.setMessage("You cancelled your: "+ appointmentId + " appointment less than the acceptable 24-Hour window. Your strikes are: "+ client.getNoShowStrikes() + " /3");
            notificationRepository.save(notif);
        }

        appointment.setCancellationReason(reason);
        appointmentRepository.save(appointment);
        auditLogService.log(cancelledBy, AuditAction.APPOINTMENT_CANCELLED, "Appointment", appointmentId, reason);

        AvailabilitySlot slot = appointment.getSlot();
        slot.setStatus(SlotStatus.AVAILABLE);
        availabilitySlotRepository.save(slot);

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
            throw new InvalidRequest("Cannot mark No Show until 20 minutes has passed from the appointment start time");
        }

        appointment.setStatus(AppointmentStatus.NO_SHOW);
        appointmentRepository.save(appointment);

        User client = appointment.getClient();
        client.setNoShowStrikes(client.getNoShowStrikes()+ 1);

        if (client.getNoShowStrikes() >= 3) {
            client.setBlacklisted(true);
        }

        userRepository.save(client);
        auditLogService.log(appointment.getAdvisor().getUser(), AuditAction.APPOINTMENT_NO_SHOW,
                "Appointment", appointmentId, "Marked no-show; client strikes: " + client.getNoShowStrikes());

        Notification notif = new Notification();
        notif.setUser(client);
        notif.setRelatedAppointment(appointment);
        notif.setType(NotificationType.APPOINTMENT_NO_SHOW);
        notif.setMessage("You were marked as No Show for your: "+ appointmentId + " appointment. Your strikes are: "+ client.getNoShowStrikes() + " /3");
        notificationRepository.save(notif);

        return appointment;
    }

    @Transactional
    public Appointment excuseNoShow(Long appointmentId, String reason, User admin) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new InformationNotFound("Appointment not found"));

        if (!appointment.getStatus().equals(AppointmentStatus.NO_SHOW)){
            throw new InvalidRequest("Appointment was not marked as a No-Show");
        }

        User client = appointment.getClient();
        appointment.setExcused(true);
        appointment.setExcuseReason(reason);
        client.setNoShowStrikes(client.getNoShowStrikes() > 0 ? client.getNoShowStrikes() - 1 : 0 );
        client.setBlacklisted(client.getNoShowStrikes() >= 3);

        userRepository.save(client);
        auditLogService.log(admin, AuditAction.NO_SHOW_EXCUSED, "Appointment", appointmentId,
                "No show excused: " + reason + "; client strikes: " + client.getNoShowStrikes());

        Notification excuseNotification = new Notification();
        excuseNotification.setUser(client);
        excuseNotification.setType(NotificationType.APPOINTMENT_NO_SHOW);
        excuseNotification.setMessage("Your No Show was excused. Reason: " + reason);
        excuseNotification.setRelatedAppointment(appointment);
        notificationRepository.save(excuseNotification);

        return appointmentRepository.save(appointment);
    }

    //admin gated, no role checks
    @Transactional
    public void adminCancelAppointment(Long appointmentId, String reason, User admin) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new InformationNotFound("Appointment not found"));

        if (appointment.getStatus().equals(AppointmentStatus.CANCELLED) || appointment.getStatus().equals(AppointmentStatus.NO_SHOW)) {
            throw new InvalidRequest("Cannot cancel an appointment that is marked as Cancelled or No Show");
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancelledAt(LocalDateTime.now());
        appointment.setCancellationReason(reason);
        appointmentRepository.save(appointment);

        AvailabilitySlot slot = appointment.getSlot();
        slot.setStatus(SlotStatus.AVAILABLE);
        availabilitySlotRepository.save(slot);

        auditLogService.log(admin, AuditAction.APPOINTMENT_CANCELLED_BY_ADMIN, "Appointment", appointmentId, reason);

        Notification notif = new Notification();
        notif.setUser(appointment.getAdvisor().getUser());
        notif.setRelatedAppointment(appointment);
        notif.setType(NotificationType.APPOINTMENT_CANCELLED);
        notif.setMessage("The appointment with ID: " + appointmentId + " has been cancelled due to: " + reason);
        notificationRepository.save(notif);
    }
}