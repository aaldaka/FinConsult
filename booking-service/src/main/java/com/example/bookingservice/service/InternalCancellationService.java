package com.example.bookingservice.service;

import com.example.bookingservice.client.AdvisorClient;
import com.example.bookingservice.client.AdvisorInfo;
import com.example.bookingservice.client.UserClient;
import com.example.bookingservice.integration.NotificationPublisher;
import com.example.bookingservice.model.Appointment;
import com.example.bookingservice.model.AvailabilitySlot;
import com.example.bookingservice.model.enums.AppointmentStatus;
import com.example.bookingservice.model.enums.NotificationType;
import com.example.bookingservice.model.enums.SlotStatus;
import com.example.bookingservice.model.response.CancellationSummary;
import com.example.bookingservice.repository.AppointmentRepository;
import com.example.bookingservice.repository.AvailabilitySlotRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

// the cascades the user service asks for when an advisor or a client is deactivated. Neither adds a strike, the
// cancelled side did nothing wrong. Every appointment is cancelled in its own transaction, so a conflict on one
// (someone changed it at the same time) is counted as failed and the others still go through. The calls to the user
// service and the notifications run outside those transactions
@Slf4j
@Service
public class InternalCancellationService {

    static final String ADVISOR_REASON = "Advisor is no longer available";
    static final String CLIENT_REASON = "Client account was deactivated";
    private static final List<AppointmentStatus> CANCELLABLE = List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED);
    private static final DateTimeFormatter SLOT_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");

    private final AppointmentRepository appointmentRepository;
    private final AvailabilitySlotRepository availabilitySlotRepository;
    private final AdvisorClient advisorClient;
    private final UserClient userClient;
    private final NotificationPublisher notificationPublisher;
    private final TransactionTemplate transactionTemplate;

    public InternalCancellationService(AppointmentRepository appointmentRepository, AvailabilitySlotRepository availabilitySlotRepository,
                                       AdvisorClient advisorClient, UserClient userClient, NotificationPublisher notificationPublisher,
                                       PlatformTransactionManager transactionManager) {
        this.appointmentRepository = appointmentRepository;
        this.availabilitySlotRepository = availabilitySlotRepository;
        this.advisorClient = advisorClient;
        this.userClient = userClient;
        this.notificationPublisher = notificationPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    private String slotTime(Appointment appointment) {
        return appointment.getSlot().getStartTime().format(SLOT_TIME);
    }

    // a failing notification must not turn a cancellation that is already saved into an error
    private void safely(String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.error("{} failed after the change was saved", what, e);
        }
    }

    // a missing or blank reason falls back to the default text
    private static String reasonOr(String given, String fallback) {
        return (given == null || given.isBlank()) ? fallback : given.trim();
    }

    // used when user-service deactivates an advisor: each appointment is cancelled as if the advisor cancelled it,
    // the slot is freed and the client is told
    public CancellationSummary cancelForAdvisor(Long advisorId, String givenReason, Long adminId) {
        String reason = reasonOr(givenReason, ADVISOR_REASON);
        List<Long> ids = idsOf(appointmentRepository.findByAdvisorIdAndStatusIn(advisorId, CANCELLABLE));
        String advisorName = advisorName(advisorId);

        int cancelled = 0;
        int failed = 0;
        for (Long id : ids) {
            Appointment appointment;
            try {
                appointment = transactionTemplate.execute(status -> cancelInTransaction(id, reason));
            } catch (RuntimeException e) {
                failed++;
                log.warn("Could not cancel appointment {} of deactivated advisor {}: {}", id, advisorId, e.getMessage());
                continue;
            }
            if (appointment == null) {
                continue; // no longer pending or confirmed, nothing to do
            }
            cancelled++;
            safely("Cancellation notification", () -> notificationPublisher.publish(appointment.getClientId(), NotificationType.APPOINTMENT_CANCELLED,
                    advisorName + " cancelled the appointment on " + slotTime(appointment) + ". Reason: " + reason, appointment.getId()));
        }

        log.info("Advisor {} deactivated (admin {}): {} appointment(s) cancelled, {} failed", advisorId, adminId, cancelled, failed);
        return new CancellationSummary(cancelled, failed);
    }

    // used when user-service deactivates a client, like an admin cancel: the slot is freed and the advisor is told
    public CancellationSummary cancelForClient(Long clientId, String givenReason, Long adminId) {
        String reason = reasonOr(givenReason, CLIENT_REASON);
        List<Appointment> upcoming = appointmentRepository.findByClientIdAndStatusIn(clientId, CANCELLABLE);
        Map<Long, AdvisorInfo> advisors = advisorsOf(upcoming);
        String clientName = clientName(clientId);

        int cancelled = 0;
        int failed = 0;
        for (Long id : idsOf(upcoming)) {
            Appointment appointment;
            try {
                appointment = transactionTemplate.execute(status -> cancelInTransaction(id, reason));
            } catch (RuntimeException e) {
                failed++;
                log.warn("Could not cancel appointment {} of deactivated client {}: {}", id, clientId, e.getMessage());
                continue;
            }
            if (appointment == null) {
                continue;
            }
            cancelled++;
            AdvisorInfo advisor = advisors.get(appointment.getAdvisorId());
            if (advisor == null) {
                log.warn("Appointment {} cancelled, but advisor {} could not be looked up to notify", appointment.getId(), appointment.getAdvisorId());
                continue;
            }
            safely("Cancellation notification", () -> notificationPublisher.publish(advisor.userId(), NotificationType.APPOINTMENT_CANCELLED,
                    "Your appointment with " + clientName + " on " + slotTime(appointment) + " was cancelled. Reason: " + reason,
                    appointment.getId()));
        }

        log.info("Client {} deactivated (admin {}): {} appointment(s) cancelled, {} failed", clientId, adminId, cancelled, failed);
        return new CancellationSummary(cancelled, failed);
    }

    // read again inside the transaction: if it was cancelled, completed or marked in the meantime it is left alone.
    // A conflicting change between this read and the commit fails the @Version check
    private Appointment cancelInTransaction(Long appointmentId, String reason) {
        Appointment appointment = appointmentRepository.findById(appointmentId).orElse(null);
        if (appointment == null || !CANCELLABLE.contains(appointment.getStatus())) {
            return null;
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancelledAt(LocalDateTime.now());
        appointment.setCancellationReason(reason);
        appointmentRepository.save(appointment);

        AvailabilitySlot slot = appointment.getSlot();
        slot.setStatus(SlotStatus.AVAILABLE);
        availabilitySlotRepository.save(slot);
        return appointment;
    }

    private List<Long> idsOf(List<Appointment> appointments) {
        return appointments.stream().map(Appointment::getId).toList();
    }

    // names only go into the messages, so an unavailable user service gives a plain fallback instead of stopping the cascade
    private String advisorName(Long advisorId) {
        try {
            return advisorClient.getById(advisorId).displayName();
        } catch (RuntimeException e) {
            log.warn("Could not fetch the name of advisor {} for the messages: {}", advisorId, e.getMessage());
            return "Your advisor";
        }
    }

    private String clientName(Long clientId) {
        try {
            String name = userClient.names(List.of(clientId)).get(clientId);
            return name != null ? name : "User " + clientId;
        } catch (RuntimeException e) {
            log.warn("Could not fetch the name of client {} for the messages: {}", clientId, e.getMessage());
            return "User " + clientId;
        }
    }

    private Map<Long, AdvisorInfo> advisorsOf(List<Appointment> appointments) {
        if (appointments.isEmpty()) {
            return Map.of();
        }
        try {
            return advisorClient.lookup(appointments.stream().map(Appointment::getAdvisorId).distinct().toList());
        } catch (RuntimeException e) {
            log.warn("Could not look up the advisors to notify: {}", e.getMessage());
            return Map.of();
        }
    }
}
