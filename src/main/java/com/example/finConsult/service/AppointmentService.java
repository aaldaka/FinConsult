package com.example.finConsult.service;

import com.example.finConsult.exception.InformationNotFound;
import com.example.finConsult.exception.InvalidRequest;
import com.example.finConsult.exception.SlotUnavailable;
import com.example.finConsult.model.*;
import com.example.finConsult.model.enums.*;
import com.example.finConsult.repository.*;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Core appointment lifecycle: booking, advisor confirmation, cancellation, no-show
 * tracking (including the client strike/blacklist rules) and admin overrides.
 */
@Slf4j
@Service
@AllArgsConstructor
public class AppointmentService {
    private static final int CANCELLATION_STRIKE_WINDOW_HOURS = 24;
    private static final int LATE_CANCEL_RELEASE_CUTOFF_HOURS = 6;
    private static final Set<String> SORTABLE = Set.of("createdAt", "status", "slot.startTime");

    private final AppointmentRepository appointmentRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final AdvisorRepository advisorRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final KycDocumentRepository kycDocumentRepository;
    private final NotificationService notificationService;

    /** Rejects sort fields outside the allow-list so callers can't sort on an unindexed or sensitive column. */
    private void checkSort(Sort sort) { //validation
        for (Sort.Order o : sort) {
            if (!SORTABLE.contains(o.getProperty())) {
                throw new InvalidRequest("Cannot sort by " + o.getProperty() + ". Allowed: " + SORTABLE);
            }
        }
    }

    /**
     * Books a slot, leaving it PENDING until the advisor confirms. Checks the advisor
     * is active, the specialty matches the service (GENERAL advisors take anything),
     * and — if the service requires KYC — that the client has an approved document.
     */
    @Transactional
    public Appointment bookSlot(User client, Long slotId, Long serviceId) {
        AvailabilitySlot slot = availabilitySlotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new InformationNotFound("Slot not found"));

        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            log.warn("Booking rejected: slot unavailable (slot {})", slotId);
            throw new SlotUnavailable("This slot is no longer available");
        }

        if (!slot.getAdvisor().isActive()) {
            log.warn("Booking rejected: advisor inactive (advisor {})", slot.getAdvisor().getId());
            throw new InvalidRequest("This advisor is no longer available");
        }

        ServiceType serviceType = serviceTypeRepository.findById(serviceId)
                .orElseThrow(() -> new InformationNotFound("Service type not found"));

        Specialty requested = serviceType.getRequiredSpecialty();
        Specialty actual = slot.getAdvisor().getSpecialty();
        if (requested != null && actual != requested && actual != Specialty.GENERAL) { // checks if the requested specialty matches the slot's advisor
            log.warn("Booking rejected: wrong specialty (advisor {}, service {})", slot.getAdvisor().getId(), serviceId);
            throw new InvalidRequest("This advisor does not offer the selected service");
        }

        // added, checks if the slot/specialty requires a KYC and if it is present in the client's records
        if (serviceType.isRequiresKyc()) { // null need to check if the client has any approved docs on his record
            DocumentType needed = serviceType.getRequiredDocumentType();
            boolean ok = (needed == null) ? kycDocumentRepository.existsByUserAndStatus(client, KycStatus.APPROVED)
                    : kycDocumentRepository.existsByUserAndDocumentTypeAndStatus(client, needed, KycStatus.APPROVED);
            if (!ok) {
                log.warn("Booking rejected: missing KYC (client {}, service {})", client.getId(), serviceId);
                throw new InvalidRequest(needed == null ? "This service requires an approved KYC document" : "This service requires an approved " + needed + " document");
            }
        }

        Appointment appointment = new Appointment();
        appointment.setClient(client);
        appointment.setAdvisor(slot.getAdvisor());
        appointment.setSlot(slot);
        appointment.setServiceType(serviceType);
        appointment.setStatus(AppointmentStatus.PENDING);
        appointment = appointmentRepository.save(appointment);   // <- new line
        slot.setStatus(SlotStatus.BOOKED);
        availabilitySlotRepository.save(slot);

        // an advisor gets notified so they can confirm their availability for the booked slot
        notificationService.create(slot.getAdvisor().getUser(), NotificationType.APPOINTMENT_BOOKED,
                "Client: " + client.getId() + " has booked an appointment. Pending your confirmation.", appointment);

        auditLogService.log(client, AuditAction.APPOINTMENT_BOOKED, "Appointment", appointment.getId(),
                "User " + client.getId() + " booked appointment " + appointment.getId());

        log.info("Appointment {} booked by client {} with advisor {}", appointment.getId(), client.getId(), slot.getAdvisor().getId());
        return appointment;
    }

    /** Only the owning advisor may confirm, and only while it's still pending. */
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
        notificationService.create(appointment.getClient(), NotificationType.APPOINTMENT_CONFIRMED,
                "Your appointment has been confirmed!", appointment);

        auditLogService.log(appointment.getAdvisor().getUser(), AuditAction.APPOINTMENT_CONFIRMED,
                "Appointment", appointmentId, "User " + appointment.getAdvisor().getUser().getId() + " confirmed appointment " + appointmentId);

        log.info("Appointment {} confirmed by advisor {}", appointmentId, advisorId);
        return appointmentRepository.save(appointment);
    }

    /**
     * Advisor cancellations never add a strike. Client cancellations only add one
     * (and blacklist at 3) when done less than 24h before the slot — too late for the
     * advisor to re-fill it, so it's treated like a no-show. The freed slot is reopened
     * for booking unless the client cancelled inside the last {@value
     * #LATE_CANCEL_RELEASE_CUTOFF_HOURS} hours before the slot, where there's realistically
     * no time left for anyone else to book and show up.
     */
    @Transactional
    public void cancelAppointment(Long appointmentId, User cancelledBy, String additionalNotes) {
        String notes = (additionalNotes == null || additionalNotes.isBlank()) ? null : additionalNotes.trim();
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new InformationNotFound("Appointment not found"));

        boolean isClient = appointment.getClient().getId().equals(cancelledBy.getId());
        boolean isAdvisor = appointment.getAdvisor().getUser().getId().equals(cancelledBy.getId());

        if (!isClient && !isAdvisor) {
            throw new InvalidRequest("You are not authorized to cancel this appointment");
        }

        if (appointment.getStatus().equals(AppointmentStatus.CANCELLED) || appointment.getStatus().equals(AppointmentStatus.NO_SHOW)){
            throw new InvalidRequest("Cannot cancel an appointment that is marked as Cancelled or No Show");
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancelledAt(LocalDateTime.now());
        String reason;
        boolean withinStrikeWindow = false;
        boolean shouldRelease;

        if (isAdvisor) {
            reason = notes != null ? notes : "Cancelled by Advisor";
            shouldRelease = true;
        } else if (LocalDateTime.now().isBefore(appointment.getSlot().getStartTime().minusHours(CANCELLATION_STRIKE_WINDOW_HOURS))){
            reason = notes != null ? notes : "Cancelled by Client";
            shouldRelease = true;
        } else {
            withinStrikeWindow = true;
            // still worth re-opening if there's enough notice left for someone else to realistically book and show up
            shouldRelease = LocalDateTime.now().isBefore(appointment.getSlot().getStartTime().minusHours(LATE_CANCEL_RELEASE_CUTOFF_HOURS));
            reason = "Cancelled by Client less than the accepted cancellation window"
                    + (notes != null ? " — reason given: " + notes : "");
                User client = appointment.getClient();
                client.setNoShowStrikes(client.getNoShowStrikes() + 1);

            boolean newlyBlacklisted = !client.isBlacklisted() && client.getNoShowStrikes() >= 3;
            if (client.getNoShowStrikes() >= 3) {
                client.setBlacklisted(true);
            }
            userRepository.save(client);

            log.info("Strike added to client {} (now {} strikes)", client.getId(), client.getNoShowStrikes());

            if (newlyBlacklisted) {
                auditLogService.log(client, AuditAction.CLIENT_BLACKLISTED, "User", client.getId(),
                        "User " + client.getId() + " blacklisted after reaching " + client.getNoShowStrikes() + " strikes");
                log.warn("Client {} blacklisted after reaching {} strikes", client.getId(), client.getNoShowStrikes());
            }

            notificationService.create(client, NotificationType.APPOINTMENT_NO_SHOW,
                    "You cancelled your: " + appointmentId + " appointment less than the acceptable 24 Hour window. Your strikes are: "
                            + client.getNoShowStrikes() + " /3", appointment);
        }

        appointment.setCancellationReason(reason);
        appointmentRepository.save(appointment);
        auditLogService.log(cancelledBy, AuditAction.APPOINTMENT_CANCELLED, "Appointment", appointmentId, reason);

        log.info("Appointment {} cancelled by user {} ({}, within 24h strike window: {}, slot released: {})",
                appointmentId, cancelledBy.getId(), isAdvisor ? "advisor" : "client", withinStrikeWindow, shouldRelease);

        if (shouldRelease) {
            AvailabilitySlot slot = appointment.getSlot();
            slot.setStatus(SlotStatus.AVAILABLE);
            availabilitySlotRepository.save(slot);
        }

        User whoToNotify = isClient ? appointment.getAdvisor().getUser() : appointment.getClient(); //notify the other party of the cancellation
        notificationService.create(whoToNotify, NotificationType.APPOINTMENT_CANCELLED,
                "The appointment with ID: " + appointmentId + " has been cancelled due to: " + reason, appointment);
    }

    public List<Appointment> getClientAppointments(User client, AppointmentStatus status, Sort sortBy) {
        checkSort(sortBy);
        return (status == null) ? appointmentRepository.findByClient(client, sortBy) : appointmentRepository.findByClientAndStatus(client, status, sortBy);
    }

    public List<Appointment> getAdvisorAppointments(Long advisorId, AppointmentStatus status, Sort sortBy) {
        checkSort(sortBy);
        Advisor advisor = advisorRepository.findById(advisorId)
                .orElseThrow(() -> new InformationNotFound("Advisor not found"));

        return (status == null) ? appointmentRepository.findByAdvisor(advisor, sortBy) : appointmentRepository.findByAdvisorAndStatus(advisor, status, sortBy);
    }

    /** Always adds a strike (no-shows are never the advisor's fault). Requires a 20-minute grace period first. */
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

        boolean newlyBlacklisted = !client.isBlacklisted() && client.getNoShowStrikes() >= 3;
        if (client.getNoShowStrikes() >= 3) {
            client.setBlacklisted(true);
        }

        userRepository.save(client);
        auditLogService.log(appointment.getAdvisor().getUser(), AuditAction.APPOINTMENT_NO_SHOW,
                "Appointment", appointmentId, "Marked no-show; client strikes: " + client.getNoShowStrikes());

        log.info("Appointment {} marked no-show by advisor {}", appointmentId, advisorId);
        log.info("Strike added to client {} (now {} strikes)", client.getId(), client.getNoShowStrikes());

        if (newlyBlacklisted) {
            auditLogService.log(client, AuditAction.CLIENT_BLACKLISTED, "User", client.getId(),
                    "User " + client.getId() + " blacklisted after reaching " + client.getNoShowStrikes() + " strikes");
            log.warn("Client {} blacklisted after reaching {} strikes", client.getId(), client.getNoShowStrikes());
        }

        notificationService.create(client, NotificationType.APPOINTMENT_NO_SHOW,
                "You were marked as No Show for your: " + appointmentId + " appointment. Your strikes are: "
                        + client.getNoShowStrikes() + " /3", appointment);

        return appointment;
    }

    /** Reverses a no-show: removes one strike and un-blacklists the client if that brings them under 3. */
    @Transactional
    public Appointment excuseNoShow(Long appointmentId, String reason, User admin) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new InformationNotFound("Appointment not found"));

        if (reason == null || reason.isBlank()) {
            throw new InvalidRequest("A reason is required");
        }

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

        log.info("No-show excused for appointment {} by admin {}", appointmentId, admin.getId());

        notificationService.create(client, NotificationType.APPOINTMENT_NO_SHOW,
                "Your No Show was excused. Reason: " + reason, appointment);

        return appointmentRepository.save(appointment);
    }

    /** Used for cascading cancellations (e.g. account deactivation) — never adds a strike, client isn't at fault. */
    @Transactional
    public void adminCancelAppointment(Long appointmentId, String reason, User admin) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new InformationNotFound("Appointment not found"));

        if (appointment.getStatus().equals(AppointmentStatus.CANCELLED) || appointment.getStatus().equals(AppointmentStatus.NO_SHOW)) {
            throw new InvalidRequest("Cannot cancel an appointment that is marked as Cancelled or No Show");
        }

        if (reason == null || reason.isBlank()) {
            throw new InvalidRequest("A reason is required");
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancelledAt(LocalDateTime.now());
        appointment.setCancellationReason(reason);
        appointmentRepository.save(appointment);

        AvailabilitySlot slot = appointment.getSlot();
        slot.setStatus(SlotStatus.AVAILABLE);
        availabilitySlotRepository.save(slot);

        auditLogService.log(admin, AuditAction.APPOINTMENT_CANCELLED_BY_ADMIN, "Appointment", appointmentId, reason);

        log.info("Appointment {} cancelled by admin {}", appointmentId, admin.getId());

        notificationService.create(appointment.getAdvisor().getUser(), NotificationType.APPOINTMENT_CANCELLED,
                "The appointment with ID: " + appointmentId + " has been cancelled due to: " + reason, appointment);
    }
}