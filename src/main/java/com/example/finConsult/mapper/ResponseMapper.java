package com.example.finConsult.mapper;

import com.example.finConsult.model.*;
import com.example.finConsult.model.response.*;
import com.example.finConsult.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Maps entities to their public response DTOs, keeping internal fields (and full
 * related entities) out of API responses.
 */
@Component
@RequiredArgsConstructor
public class ResponseMapper {

    private final ProfileRepository profileRepository;
    private static final String DEFAULT_PICTURE_PATH = "/default.jpg";


    public ProfileUpdateResponse toProfile(Profile profile) {
        return new ProfileUpdateResponse(
                profile.getFirstName(), profile.getLastName(), profile.getPhone(),
                profile.getDateOfBirth(),
                profile.getProfilePicturePath() != null ? profile.getProfilePicturePath() : DEFAULT_PICTURE_PATH);
    }

    public KycDocResponse toKyc(KycDocument d) {
        return new KycDocResponse(
                d.getId(), d.getUser().getEmail(), d.getDocumentType(),
                d.getOriginalFileName(), d.getFileSize(), d.getStatus(),
                d.getRejectionReason(), d.getCreatedAt(), d.getReviewedAt());
    }

    public AuditLogResponse toAuditLog(AuditLog entry) {
        String actor = entry.getActor() != null ? entry.getActor().getEmail() : "SYSTEM";
        return new AuditLogResponse(entry.getId(), actor, entry.getAction(), entry.getEntityType(),
                entry.getEntityId(), entry.getDetails(), entry.getTimestamp());
    }

    public NotificationResponse toNotification(Notification notif) {
        return new NotificationResponse(
                notif.getId(), notif.getType(), notif.getMessage(), notif.isRead(),
                notif.getRelatedAppointment() == null ? null : notif.getRelatedAppointment().getId(),
                notif.getCreatedAt());
    }

    private String nameOf(User user) {
        return profileRepository.findByUser(user)
                .map(p -> p.getFirstName() + " " + p.getLastName())
                .orElse("Unknown");
    }

    public AdvisorResponse toAdvisor(Advisor a) {
        return new AdvisorResponse(a.getId(), nameOf(a.getUser()), a.getTitle(),
                a.getSpecialty(), a.getYearsOfExperience(), a.isActive());
    }

    public SlotResponse toSlot(AvailabilitySlot s) {
        return new SlotResponse(s.getId(), s.getAdvisor().getId(),
                s.getStartTime(), s.getEndTime(), s.getStatus());
    }

    public ServiceTypeResponse toServiceType(ServiceType s) {
        return new ServiceTypeResponse(s.getId(), s.getName(), s.getDescription(), s.getDurationMinutes(),
                s.isRequiresKyc(), s.getRequiredDocumentType(), s.getRequiredSpecialty());
    }

    public CertificateResponse toCertificate(ApplicationCertificate c) {
        return new CertificateResponse(c.getId(), c.getOriginalFileName(), c.getFileSize());
    }

    public AdvisorApplicationResponse toApplication(AdvisorApplication application, List<ApplicationCertificate> certificates) {
        return new AdvisorApplicationResponse(application.getId(), application.getUser().getEmail(),
                nameOf(application.getUser()), application.getTitle(), application.getSpecialty(),
                application.getYearsOfExperience(), application.getStatus(), application.getRejectionReason(),
                application.getCreatedAt(), application.getReviewedAt(),
                certificates.stream().map(this::toCertificate).toList());
    }

    public AppointmentResponse toAppointment(Appointment a) {
        return new AppointmentResponse(a.getId(), a.getStatus(), a.getNotes(),
                a.getCancellationReason(), a.isExcused(), a.getExcuseReason(),
                a.getAdvisor().getId(), nameOf(a.getAdvisor().getUser()), a.getAdvisor().getSpecialty(),
                nameOf(a.getClient()), a.getServiceType().getName(),
                a.getSlot().getStartTime(), a.getSlot().getEndTime(), a.getCreatedAt());
    }
}