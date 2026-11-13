package com.example.bookingservice.service;

import com.example.bookingservice.client.AdvisorClient;
import com.example.bookingservice.client.AdvisorInfo;
import com.example.bookingservice.client.StrikeClient;
import com.example.bookingservice.client.StrikeResult;
import com.example.bookingservice.client.UserClient;
import com.example.bookingservice.exception.InformationNotFound;
import com.example.bookingservice.exception.InvalidRequest;
import com.example.bookingservice.exception.SlotUnavailable;
import com.example.bookingservice.integration.AuditRecorder;
import com.example.bookingservice.integration.KycCheck;
import com.example.bookingservice.integration.NotificationPublisher;
import com.example.bookingservice.model.Appointment;
import com.example.bookingservice.model.AvailabilitySlot;
import com.example.bookingservice.model.ServiceType;
import com.example.bookingservice.model.enums.AppointmentStatus;
import com.example.bookingservice.model.enums.AuditAction;
import com.example.bookingservice.model.enums.DocumentType;
import com.example.bookingservice.model.enums.NotificationType;
import com.example.bookingservice.model.enums.SlotStatus;
import com.example.bookingservice.model.enums.Specialty;
import com.example.bookingservice.repository.AppointmentRepository;
import com.example.bookingservice.repository.AvailabilitySlotRepository;
import com.example.bookingservice.repository.ServiceTypeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;

// book, confirm, cancel, no-show, excuse and the listings. The calls to the user service and the stubs never run inside a database transaction:
// they happen before it opens (account status, advisor) or after it has committed (names, strikes, notifications, audit)
@Slf4j
@Service
public class AppointmentService {

    private static final int CANCELLATION_STRIKE_WINDOW_HOURS = 24;
    private static final int LATE_CANCEL_RELEASE_CUTOFF_HOURS = 6;
    private static final int NO_SHOW_GRACE_MINUTES = 20;
    private static final Set<String> SORTABLE = Set.of("createdAt", "status", "slot.startTime");
    private static final DateTimeFormatter SLOT_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");

    private final AppointmentRepository appointmentRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final AdvisorClient advisorClient;
    private final UserClient userClient;
    private final StrikeClient strikeClient;
    private final KycCheck kycCheck;
    private final NotificationPublisher notificationPublisher;
    private final AuditRecorder auditRecorder;
    private final AccountStatusService accountStatusService;
    private final TransactionTemplate transactionTemplate;

    public AppointmentService(AppointmentRepository appointmentRepository, AvailabilitySlotRepository availabilitySlotRepository,
                              ServiceTypeRepository serviceTypeRepository, AdvisorClient advisorClient, UserClient userClient,
                              StrikeClient strikeClient, KycCheck kycCheck, NotificationPublisher notificationPublisher,
                              AuditRecorder auditRecorder, AccountStatusService accountStatusService,
                              PlatformTransactionManager transactionManager) {
        this.appointmentRepository = appointmentRepository;
        this.availabilitySlotRepository = availabilitySlotRepository;
        this.serviceTypeRepository = serviceTypeRepository;
        this.advisorClient = advisorClient;
        this.userClient = userClient;
        this.strikeClient = strikeClient;
        this.kycCheck = kycCheck;
        this.notificationPublisher = notificationPublisher;
        this.auditRecorder = auditRecorder;
        this.accountStatusService = accountStatusService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    private String slotTime(Appointment appointment) {
        return appointment.getSlot().getStartTime().format(SLOT_TIME);
    }

    // a failing notification or audit call must not turn a change that is already saved into an error
    private void safely(String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.error("{} failed after the change was saved", what, e);
        }
    }

    // names are only used for messages, so an unavailable user service gives a plain fallback instead of an error
    private Map<Long, String> namesOrEmpty(Long... userIds) {
        try {
            return userClient.names(List.of(userIds));
        } catch (RuntimeException e) {
            log.warn("Could not fetch names for the messages: {}", e.getMessage());
            return Map.of();
        }
    }

    private String nameFrom(Map<Long, String> names, Long userId) {
        String name = names.get(userId);
        return name != null ? name : "User " + userId;
    }

    // books a slot, leaving it PENDING until the advisor confirms
    public Appointment bookSlot(Long clientId, Long slotId, Long serviceId) {
        accountStatusService.requireActive(clientId);
        log.info("Client {} wants slot {}", clientId, slotId);

        // outside any transaction: which advisor owns the slot, and what the user service says about them
        Long advisorId = availabilitySlotRepository.findAdvisorIdById(slotId)
                .orElseThrow(() -> new InformationNotFound("Slot not found"));
        AdvisorInfo advisor = advisorClient.getById(advisorId);

        Appointment booked;
        try {
            booked = transactionTemplate.execute(status -> bookInTransaction(clientId, slotId, serviceId, advisor));
        } catch (DataIntegrityViolationException e) { // the unique index on active appointments, the backstop for a double booking
            log.warn("Client {} refused: slot {} already has an active appointment", clientId, slotId);
            throw new SlotUnavailable("This slot is no longer available");
        }

        afterBooking(booked, clientId, advisor);
        return booked;
    }

    private Appointment bookInTransaction(Long clientId, Long slotId, Long serviceId, AdvisorInfo advisor) {
        AvailabilitySlot slot = availabilitySlotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new InformationNotFound("Slot not found"));

        log.info("Client {} got the lock on slot {} (status {})", clientId, slotId, slot.getStatus());

        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            log.warn("Client {} refused: slot {} is already {}", clientId, slotId, slot.getStatus());
            throw new SlotUnavailable("This slot is no longer available");
        }

        if (!advisor.active()) {
            log.warn("Booking rejected: advisor inactive (advisor {})", advisor.id());
            throw new InvalidRequest("This advisor is no longer available");
        }

        ServiceType serviceType = serviceTypeRepository.findById(serviceId)
                .orElseThrow(() -> new InformationNotFound("Service type not found"));

        if (!serviceType.isActive()) {
            log.warn("Booking rejected: service {} is not active", serviceId);
            throw new InvalidRequest("This service is no longer available");
        }

        Specialty requested = serviceType.getRequiredSpecialty();
        Specialty actual = advisor.specialty();
        if (requested != null && actual != requested && actual != Specialty.GENERAL) { // the advisor must offer the requested specialty
            log.warn("Booking rejected: wrong specialty (advisor {}, service {})", advisor.id(), serviceId);
            throw new InvalidRequest("This advisor does not offer the selected service");
        }

        if (serviceType.isRequiresKyc()) { // a null type means any approved document
            DocumentType needed = serviceType.getRequiredDocumentType();
            if (!kycCheck.isApproved(clientId, needed)) {
                log.warn("Booking rejected: missing KYC (client {}, service {})", clientId, serviceId);
                throw new InvalidRequest(needed == null ? "This service requires an approved KYC document" : "This service requires an approved " + needed + " document");
            }
        }

        Appointment appointment = new Appointment();
        appointment.setClientId(clientId);
        appointment.setAdvisorId(advisor.id());
        appointment.setSlot(slot);
        appointment.setServiceType(serviceType);
        appointment.setStatus(AppointmentStatus.PENDING);
        appointment = appointmentRepository.save(appointment);
        slot.setStatus(SlotStatus.BOOKED);
        availabilitySlotRepository.save(slot);
        return appointment;
    }

    // the advisor gets notified so they can confirm their availability for the booked slot
    private void afterBooking(Appointment appointment, Long clientId, AdvisorInfo advisor) {
        String clientName = nameFrom(namesOrEmpty(clientId), clientId);
        String serviceName = appointment.getServiceType().getName();

        safely("Booking notification", () -> notificationPublisher.publish(advisor.userId(), NotificationType.APPOINTMENT_BOOKED,
                clientName + " booked " + serviceName + " on " + slotTime(appointment) + ". Pending your confirmation.", appointment.getId()));
        safely("Booking audit", () -> auditRecorder.record(AuditAction.APPOINTMENT_BOOKED, clientId, appointment.getId(),
                clientName + " booked " + serviceName + " with " + advisor.displayName() + " on " + slotTime(appointment)));

        log.info("Appointment {} booked by client {} with advisor {}", appointment.getId(), clientId, advisor.id());
    }

    // only the owning advisor may confirm, and only while it is still pending
    public Appointment confirmAppointment(Long userId, Long appointmentId) {
        accountStatusService.requireActive(userId);
        AdvisorInfo advisor = advisorClient.getByUserId(userId); // 404 if the caller has no advisor profile

        Appointment confirmed = transactionTemplate.execute(status -> {
            Appointment appointment = appointmentRepository.findById(appointmentId)
                    .orElseThrow(() -> new InformationNotFound("Appointment not found"));

            if (!appointment.getAdvisorId().equals(advisor.id())) {
                throw new InvalidRequest("You can only confirm your own appointments");
            }

            if (appointment.getStatus() != AppointmentStatus.PENDING) {
                throw new InvalidRequest("Only pending appointments can be confirmed");
            }

            appointment.setStatus(AppointmentStatus.CONFIRMED);
            return appointmentRepository.save(appointment); // @Version: if a cancel got in first, the commit fails with a 409
        });

        Long clientId = confirmed.getClientId();
        String advisorName = advisor.displayName();
        String clientName = nameFrom(namesOrEmpty(clientId), clientId);
        safely("Confirmation notification", () -> notificationPublisher.publish(clientId, NotificationType.APPOINTMENT_CONFIRMED,
                advisorName + " confirmed your " + confirmed.getServiceType().getName() + " appointment on " + slotTime(confirmed) + ".", appointmentId));
        safely("Confirmation audit", () -> auditRecorder.record(AuditAction.APPOINTMENT_CONFIRMED, userId, appointmentId,
                advisorName + " confirmed the appointment with " + clientName + " on " + slotTime(confirmed)));

        log.info("Appointment {} confirmed by advisor {}", appointmentId, advisor.id());
        return confirmed;
    }

    private record Cancelled(Appointment appointment, String reason, boolean cancelledByClient, boolean lateByClient) {}

    // advisor cancellations never add a strike. A client cancellation less than 24h before the slot adds one (and can
    // blacklist) because the advisor cannot re-fill the slot in time. The slot is reopened unless the client cancelled
    // inside the last 6 hours, where nobody else can realistically book and show up
    public void cancelAppointment(Long appointmentId, Long userId, String additionalNotes) {
        accountStatusService.requireActive(userId);
        String notes = (additionalNotes == null || additionalNotes.isBlank()) ? null : additionalNotes.trim();

        Long advisorId = appointmentRepository.findAdvisorIdById(appointmentId)
                .orElseThrow(() -> new InformationNotFound("Appointment not found"));
        AdvisorInfo advisor = advisorClient.getById(advisorId); // the advisor's user id tells if the caller is the advisor

        Cancelled cancelled = transactionTemplate.execute(status -> cancelInTransaction(appointmentId, userId, notes, advisor));

        afterCancel(cancelled, userId, advisor);
    }

    private Cancelled cancelInTransaction(Long appointmentId, Long userId, String notes, AdvisorInfo advisor) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new InformationNotFound("Appointment not found"));

        boolean isClient = appointment.getClientId().equals(userId);
        boolean isAdvisor = advisor.userId().equals(userId);

        requireParticipant(isClient, isAdvisor);
        requireCancellable(appointment);

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancelledAt(LocalDateTime.now());
        String reason;
        boolean lateByClient = false;
        boolean shouldRelease;

        if (isAdvisor) {
            reason = notes != null ? notes : "Cancelled by Advisor";
            shouldRelease = true;
        } else if (LocalDateTime.now().isBefore(appointment.getSlot().getStartTime().minusHours(CANCELLATION_STRIKE_WINDOW_HOURS))) {
            reason = notes != null ? notes : "Cancelled by Client";
            shouldRelease = true;
        } else {
            lateByClient = true;
            // still worth re-opening if there is enough notice left for someone else to realistically book and show up
            shouldRelease = LocalDateTime.now().isBefore(appointment.getSlot().getStartTime().minusHours(LATE_CANCEL_RELEASE_CUTOFF_HOURS));
            reason = "Cancelled by Client less than the accepted cancellation window"
                    + (notes != null ? ", reason given: " + notes : "");
        }

        appointment.setCancellationReason(reason);
        appointmentRepository.save(appointment);

        log.info("Appointment {} cancelled by user {} ({}, within 24h strike window: {}, slot released: {})",
                appointmentId, userId, isAdvisor ? "advisor" : "client", lateByClient, shouldRelease);

        if (shouldRelease) {
            releaseSlot(appointment);
        }
        return new Cancelled(appointment, reason, isClient && !isAdvisor, lateByClient);
    }

    // only the client or the advisor of the appointment can cancel it
    private void requireParticipant(boolean isClient, boolean isAdvisor) {
        if (!isClient && !isAdvisor) {
            throw new InvalidRequest("You are not authorized to cancel this appointment");
        }
    }

    private void requireCancellable(Appointment appointment) {
        AppointmentStatus status = appointment.getStatus();
        if (status == AppointmentStatus.CANCELLED || status == AppointmentStatus.NO_SHOW || status == AppointmentStatus.COMPLETED) {
            throw new InvalidRequest("Only pending or confirmed appointments can be cancelled");
        }
    }

    private void releaseSlot(Appointment appointment) {
        AvailabilitySlot slot = appointment.getSlot();
        slot.setStatus(SlotStatus.AVAILABLE);
        availabilitySlotRepository.save(slot);
    }

    // everything here runs after the cancellation is committed, so none of it can undo it
    private void afterCancel(Cancelled cancelled, Long userId, AdvisorInfo advisor) {
        Appointment appointment = cancelled.appointment();
        Long appointmentId = appointment.getId();
        Long clientId = appointment.getClientId();

        if (cancelled.lateByClient()) {
            addLateCancellationStrike(appointment);
        }

        Map<Long, String> names = namesOrEmpty(clientId, advisor.userId());
        String cancelledByName = cancelled.cancelledByClient() ? nameFrom(names, clientId) : advisor.displayName();
        safely("Cancellation audit", () -> auditRecorder.record(AuditAction.APPOINTMENT_CANCELLED, userId, appointmentId, cancelled.reason()));

        Long whoToNotify = cancelled.cancelledByClient() ? advisor.userId() : clientId; // the other party hears about it
        safely("Cancellation notification", () -> notificationPublisher.publish(whoToNotify, NotificationType.APPOINTMENT_CANCELLED,
                cancelledByName + " cancelled the appointment on " + slotTime(appointment) + ". Reason: " + cancelled.reason(), appointmentId));
    }

    // the strike is added by the user service after the cancellation committed. If that call fails the cancellation
    // stays and the failure is logged with both ids, so the strike can be added by hand
    private void addLateCancellationStrike(Appointment appointment) {
        Long clientId = appointment.getClientId();
        Long appointmentId = appointment.getId();

        StrikeResult result;
        try {
            result = strikeClient.addStrike(clientId);
        } catch (RuntimeException e) {
            log.error("Could not add the late cancellation strike for client {} on appointment {}, the cancellation is kept",
                    clientId, appointmentId, e);
            return;
        }

        log.info("Strike added to client {} (now {} strikes)", clientId, result.noShowStrikes());

        if (result.blacklisted()) {
            safely("Blacklist audit", () -> auditRecorder.record(AuditAction.CLIENT_BLACKLISTED, clientId, clientId,
                    nameFrom(namesOrEmpty(clientId), clientId) + " blacklisted after reaching " + result.noShowStrikes() + " strikes"));
            log.warn("Client {} blacklisted after reaching {} strikes", clientId, result.noShowStrikes());
        }

        safely("Strike notification", () -> notificationPublisher.publish(clientId, NotificationType.APPOINTMENT_NO_SHOW,
                "You cancelled your appointment on " + slotTime(appointment)
                        + " less than 24 hours before it started. Strikes: " + result.noShowStrikes() + "/3", appointmentId));
    }

    // only the owning advisor, only on a confirmed appointment, and only once the 20 minute grace has passed
    public Appointment markNoShow(Long userId, Long appointmentId) {
        accountStatusService.requireActive(userId);
        AdvisorInfo advisor = advisorClient.getByUserId(userId); // 404 if the caller has no advisor profile

        Appointment marked = transactionTemplate.execute(status -> {
            Appointment appointment = appointmentRepository.findById(appointmentId)
                    .orElseThrow(() -> new InformationNotFound("Appointment not found"));

            if (!appointment.getAdvisorId().equals(advisor.id())) {
                throw new InvalidRequest("You can only mark your own appointments");
            }

            if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
                throw new InvalidRequest("Only confirmed appointments can be marked as no-show");
            }

            if (appointment.getSlot().getStartTime().plusMinutes(NO_SHOW_GRACE_MINUTES).isAfter(LocalDateTime.now())) {
                throw new InvalidRequest("Cannot mark No Show until 20 minutes has passed from the appointment start time");
            }

            appointment.setStatus(AppointmentStatus.NO_SHOW);
            return appointmentRepository.save(appointment);
        });

        afterNoShow(marked, userId);
        return marked;
    }

    // a no-show always adds a strike (it is never the advisor's fault). The user service adds it after the commit, so if
    // that call fails the no-show stays, the failure is logged with both ids and the strike can be added by hand
    private void afterNoShow(Appointment appointment, Long advisorUserId) {
        Long clientId = appointment.getClientId();
        Long appointmentId = appointment.getId();

        StrikeResult result = null;
        try {
            result = strikeClient.addStrike(clientId);
            log.info("Strike added to client {} (now {} strikes)", clientId, result.noShowStrikes());
        } catch (RuntimeException e) {
            log.error("Could not add the no-show strike for client {} on appointment {}, the no-show is kept",
                    clientId, appointmentId, e);
        }
        StrikeResult strike = result;

        String clientName = nameFrom(namesOrEmpty(clientId), clientId);
        String strikes = strike == null ? "" : "; strikes: " + strike.noShowStrikes() + "/3";
        safely("No-show audit", () -> auditRecorder.record(AuditAction.APPOINTMENT_NO_SHOW, advisorUserId, appointmentId,
                "Marked " + clientName + " as a no-show for " + slotTime(appointment) + strikes));

        if (strike != null && strike.blacklisted()) {
            safely("Blacklist audit", () -> auditRecorder.record(AuditAction.CLIENT_BLACKLISTED, clientId, clientId,
                    clientName + " blacklisted after reaching " + strike.noShowStrikes() + " strikes"));
            log.warn("Client {} blacklisted after reaching {} strikes", clientId, strike.noShowStrikes());
        }

        safely("No-show notification", () -> notificationPublisher.publish(clientId, NotificationType.APPOINTMENT_NO_SHOW,
                "You were marked as a no-show for your appointment on " + slotTime(appointment)
                        + (strike == null ? "." : ". Strikes: " + strike.noShowStrikes() + "/3"), appointmentId));

        log.info("Appointment {} marked no-show by advisor user {}", appointmentId, advisorUserId);
    }

    // reverses a no-show: the reason is required, and a no-show can only be excused once
    public Appointment excuseNoShow(Long adminId, Long appointmentId, String reason) {
        accountStatusService.requireActive(adminId);

        Appointment excused = transactionTemplate.execute(status -> {
            Appointment appointment = appointmentRepository.findById(appointmentId)
                    .orElseThrow(() -> new InformationNotFound("Appointment not found"));

            if (reason == null || reason.isBlank()) {
                throw new InvalidRequest("A reason is required");
            }

            if (appointment.getStatus() != AppointmentStatus.NO_SHOW) {
                throw new InvalidRequest("Appointment was not marked as a No-Show");
            }

            if (appointment.isExcused()) {
                throw new InvalidRequest("This no-show was already excused");
            }

            appointment.setExcused(true);
            appointment.setExcuseReason(reason);
            return appointmentRepository.save(appointment);
        });

        afterExcuse(excused, adminId, reason);
        return excused;
    }

    // the strike is removed by the user service after the commit, with the same failure handling as the strike that
    // is added: the excuse stays and the failure is logged so the strike can be removed by hand
    private void afterExcuse(Appointment appointment, Long adminId, String reason) {
        Long clientId = appointment.getClientId();
        Long appointmentId = appointment.getId();

        StrikeResult result = null;
        try {
            result = strikeClient.removeStrike(clientId);
            log.info("Strike removed from client {} (now {} strikes)", clientId, result.noShowStrikes());
        } catch (RuntimeException e) {
            log.error("Could not remove the strike of client {} for the excused no-show on appointment {}, the excuse is kept",
                    clientId, appointmentId, e);
        }
        StrikeResult strike = result;

        String clientName = nameFrom(namesOrEmpty(clientId), clientId);
        String strikes = strike == null ? "" : "; strikes: " + strike.noShowStrikes() + "/3";
        safely("Excuse audit", () -> auditRecorder.record(AuditAction.NO_SHOW_EXCUSED, adminId, appointmentId,
                "Excused " + clientName + "'s no-show: " + reason + strikes));
        safely("Excuse notification", () -> notificationPublisher.publish(clientId, NotificationType.APPOINTMENT_NO_SHOW,
                "Your no-show was excused. Reason: " + reason, appointmentId));

        log.info("No-show excused for appointment {} by admin {}", appointmentId, adminId);
    }

    // rejects sort fields outside the allow-list, so callers cannot sort on a column that is not meant to be sorted on
    private void checkSort(Sort sort) {
        for (Sort.Order order : sort) {
            if (!SORTABLE.contains(order.getProperty())) {
                throw new InvalidRequest("Cannot sort by " + order.getProperty() + ". Allowed: " + SORTABLE);
            }
        }
    }

    public List<Appointment> getClientAppointments(Long clientId, AppointmentStatus status, Sort sortBy) {
        checkSort(sortBy);
        return status == null
                ? appointmentRepository.findByClientId(clientId, sortBy)
                : appointmentRepository.findByClientIdAndStatus(clientId, status, sortBy);
    }

    // the advisor id comes from the user service, a caller without an advisor profile is a 404
    public List<Appointment> getAdvisorAppointments(Long userId, AppointmentStatus status, Sort sortBy) {
        checkSort(sortBy);
        AdvisorInfo advisor = advisorClient.getByUserId(userId);
        return status == null
                ? appointmentRepository.findByAdvisorId(advisor.id(), sortBy)
                : appointmentRepository.findByAdvisorIdAndStatus(advisor.id(), status, sortBy);
    }

    // every no-show for the admin review, latest first, excused ones included
    public List<Appointment> getNoShows() {
        return appointmentRepository.findByStatus(AppointmentStatus.NO_SHOW, Sort.by("slot.startTime").descending());
    }
}
