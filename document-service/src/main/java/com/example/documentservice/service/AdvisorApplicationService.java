package com.example.documentservice.service;

import com.example.documentservice.client.UserClient;
import com.example.documentservice.client.UserInfo;
import com.example.documentservice.client.UserSummary;
import com.example.documentservice.exception.InformationNotFound;
import com.example.documentservice.exception.InvalidRequest;
import com.example.documentservice.exception.NotEligible;
import com.example.documentservice.integration.AuditRecorder;
import com.example.documentservice.integration.NotificationPublisher;
import com.example.documentservice.model.AdvisorApplication;
import com.example.documentservice.model.ApplicationCertificate;
import com.example.documentservice.model.enums.AuditAction;
import com.example.documentservice.model.enums.CertificateStatus;
import com.example.documentservice.model.enums.DocumentType;
import com.example.documentservice.model.enums.KycStatus;
import com.example.documentservice.model.enums.NotificationType;
import com.example.documentservice.model.enums.Specialty;
import com.example.documentservice.model.request.AdvisorApplicationRequest;
import com.example.documentservice.model.response.AdvisorApplicationResponse;
import com.example.documentservice.model.response.CertificateResponse;
import com.example.documentservice.model.response.MyApplicationsResponse;
import com.example.documentservice.repository.AdvisorApplicationRepository;
import com.example.documentservice.repository.ApplicationCertificateRepository;
import com.example.documentservice.repository.KycDocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// client applications to become an advisor. A client needs an approved ID or passport, can only have one pending
// application, and gets 3 attempts in total. The calls to the user service and the stubs never run inside a database
// transaction: lookups happen before it, and names, notifications, audit entries and the certificate checks after it
@Slf4j
@Service
public class AdvisorApplicationService {

    private static final int MAX_APPLICATIONS = 3;
    private static final List<String> ALLOWED_CERTIFICATE_TYPES = List.of("application/pdf", "image/jpeg", "image/png");
    private static final List<DocumentType> IDENTITY_DOCUMENTS = List.of(DocumentType.NATIONAL_ID, DocumentType.PASSPORT);

    private final AdvisorApplicationRepository applicationRepository;
    private final ApplicationCertificateRepository certificateRepository;
    private final KycDocumentRepository kycDocumentRepository;
    private final FileStorageService fileStorageService;
    private final DocumentProcessor certificateProcessor;
    private final UserClient userClient;
    private final NotificationPublisher notificationPublisher;
    private final AuditRecorder auditRecorder;
    private final TransactionTemplate transactionTemplate;

    public AdvisorApplicationService(AdvisorApplicationRepository applicationRepository, ApplicationCertificateRepository certificateRepository,
                                     KycDocumentRepository kycDocumentRepository, FileStorageService fileStorageService,
                                     DocumentProcessor certificateProcessor, UserClient userClient,
                                     NotificationPublisher notificationPublisher, AuditRecorder auditRecorder,
                                     PlatformTransactionManager transactionManager) {
        this.applicationRepository = applicationRepository;
        this.certificateRepository = certificateRepository;
        this.kycDocumentRepository = kycDocumentRepository;
        this.fileStorageService = fileStorageService;
        this.certificateProcessor = certificateProcessor;
        this.userClient = userClient;
        this.notificationPublisher = notificationPublisher;
        this.auditRecorder = auditRecorder;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    private record Saved(AdvisorApplication application, List<ApplicationCertificate> certificates) {}

    // a failing notification or audit call must not turn a change that is already saved into an error
    private void safely(String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.error("{} failed after the change was saved", what, e);
        }
    }

    // used after a change was saved: an unavailable user service gives a plain fallback instead of an error
    private UserSummary userOrFallback(Long userId) {
        try {
            UserSummary user = userClient.lookup(List.of(userId)).get(userId);
            if (user != null) {
                return user;
            }
        } catch (RuntimeException e) {
            log.warn("Could not fetch user {} for the messages: {}", userId, e.getMessage());
        }
        return new UserSummary(null, "User " + userId);
    }

    private AdvisorApplicationResponse toResponse(AdvisorApplication application, List<ApplicationCertificate> certificates, UserSummary user) {
        return new AdvisorApplicationResponse(application.getId(), user == null ? null : user.email(),
                user == null ? null : user.displayName(), application.getTitle(), application.getSpecialty(),
                application.getYearsOfExperience(), application.getStatus(), application.getRejectionReason(),
                application.getCreatedAt(), application.getReviewedAt(),
                certificates.stream().map(c -> new CertificateResponse(c.getId(), c.getOriginalFileName(), c.getFileSize(),
                        c.getStatus(), c.getDetectedType())).toList());
    }

    // saves the application and certificates, the certificates are checked after the commit
    public AdvisorApplicationResponse submit(Long applicantId, AdvisorApplicationRequest request, List<MultipartFile> certificates) {
        UserInfo applicant = userClient.getById(applicantId); // before any transaction
        if (!"CLIENT".equals(applicant.role())) {
            log.warn("Advisor application rejected: user {} is not a client", applicantId);
            throw new NotEligible("Only clients can apply to become an advisor");
        }
        if (!"ACTIVE".equals(applicant.status())) {
            log.warn("Advisor application rejected: user {} is not active", applicantId);
            throw new NotEligible("Your account must be active to apply");
        }
        if (applicant.blacklisted()) {
            log.warn("Advisor application rejected: user {} is blacklisted", applicantId);
            throw new NotEligible("Blacklisted accounts cannot apply to become an advisor");
        }

        boolean hasIdentityKyc = IDENTITY_DOCUMENTS.stream().anyMatch(type ->
                kycDocumentRepository.existsByUserIdAndDocumentTypeAndStatus(applicantId, type, KycStatus.APPROVED));
        if (!hasIdentityKyc) {
            log.warn("Advisor application rejected: user {} has no approved identity document", applicantId);
            throw new NotEligible("An approved national ID or passport is required before applying");
        }
        if (applicationRepository.existsByUserIdAndStatus(applicantId, KycStatus.PENDING)) {
            log.warn("Advisor application rejected: user {} already has one pending", applicantId);
            throw new NotEligible("You already have an application awaiting review");
        }
        long used = applicationRepository.countByUserId(applicantId);
        if (used >= MAX_APPLICATIONS) {
            log.warn("Advisor application rejected: user {} used all {} attempts", applicantId, MAX_APPLICATIONS);
            throw new NotEligible("You have used all " + MAX_APPLICATIONS + " application attempts");
        }

        if (certificates == null || certificates.isEmpty() || certificates.stream().allMatch(MultipartFile::isEmpty)) {
            throw new InvalidRequest("At least one certificate is required");
        }
        for (MultipartFile file : certificates) {
            String contentType = file.getContentType();
            if (contentType == null || !ALLOWED_CERTIFICATE_TYPES.contains(contentType)) {
                log.warn("Advisor application rejected: unsupported certificate type for user {}", applicantId);
                throw new InvalidRequest("Certificates must be PDF, JPG or PNG files");
            }
        }

        // the one-pending-per-user index stops a double submit that got past the check above at the same moment
        Saved saved = transactionTemplate.execute(status -> {
            AdvisorApplication application = new AdvisorApplication();
            application.setUserId(applicantId);
            application.setTitle(request.getTitle());
            application.setSpecialty(request.getSpecialty() != null ? request.getSpecialty() : Specialty.GENERAL);
            application.setYearsOfExperience(request.getYearsOfExperience());
            application.setStatus(KycStatus.PENDING);
            application = applicationRepository.save(application);

            List<ApplicationCertificate> stored = new ArrayList<>();
            for (MultipartFile file : certificates) {
                ApplicationCertificate certificate = new ApplicationCertificate();
                certificate.setApplication(application);
                certificate.setFilePath(fileStorageService.store(file, "advisor_applications"));
                certificate.setOriginalFileName(file.getOriginalFilename());
                certificate.setContentType(file.getContentType());
                certificate.setFileSize(file.getSize());
                stored.add(certificateRepository.save(certificate));
            }
            return new Saved(application, stored);
        });

        AdvisorApplication application = saved.application();
        certificateProcessor.processAll(application.getId(), applicantId,
                saved.certificates().stream().map(ApplicationCertificate::getId).toList());
        UserSummary user = userOrFallback(applicantId);
        safely("Application audit", () -> auditRecorder.record(AuditAction.ADVISOR_APPLICATION_SUBMITTED, applicantId,
                application.getId(), user.displayName() + " applied to become an advisor"));
        log.info("Advisor application {} submitted by user {}", application.getId(), applicantId);
        return toResponse(application, saved.certificates(), user);
    }

    // the user service is asked before the answer is built: if it is down this is a 503
    public MyApplicationsResponse getMyApplications(Long applicantId) {
        List<AdvisorApplication> applications = applicationRepository.findByUserId(applicantId);
        UserSummary user = applications.isEmpty() ? null : userClient.lookup(List.of(applicantId)).get(applicantId);
        List<AdvisorApplicationResponse> responses = applications.stream()
                .map(a -> toResponse(a, certificateRepository.findByApplication(a), user))
                .toList();
        return new MyApplicationsResponse(responses, applicationRepository.countByUserId(applicantId), MAX_APPLICATIONS);
    }

    // one call to the user service for the whole page, not one per row
    public Page<AdvisorApplicationResponse> getByStatus(KycStatus status, Pageable pageable) {
        Page<AdvisorApplication> applications = applicationRepository.findByStatus(status, pageable);
        Map<Long, UserSummary> users = applications.isEmpty() ? Map.of()
                : userClient.lookup(applications.stream().map(AdvisorApplication::getUserId).distinct().toList());
        return applications.map(a -> toResponse(a, certificateRepository.findByApplication(a), users.get(a.getUserId())));
    }

    // Promotes the applicant through the user service, then marks the application approved.
    //  1. load and check: pending, no certificate still being checked, and the user still an active, non-blacklisted client
    //  2. promote, outside any transaction. A 409 is fine when the user already is an advisor (an earlier attempt got
    //     that far), otherwise it stays a 409. Any other failure is a 503 and the application stays PENDING
    //  3. a short transaction: reload with the force-increment lock, still PENDING, then APPROVED
    //  4. after the commit: the notification and the audit entry
    // Known gap: if another admin rejects the application between 2 and 3, the user has been promoted but the
    // application ends up REJECTED. The promotion is not undone; that needs a person to look at it. Retrying an approval
    // is safe: a user who was already promoted is recognised in step 1 and 2 and the application is just marked approved
    public AdvisorApplicationResponse approve(Long id, Long adminId) {
        AdvisorApplication application = findPending(id);
        boolean stillChecking = certificateRepository.findByApplication(application).stream()
                .anyMatch(c -> c.getStatus() == CertificateStatus.PROCESSING);
        if (stillChecking) {
            throw new InvalidRequest("Certificates are still being checked, try again in a moment");
        }
        Long applicantId = application.getUserId();
        boolean promotedBefore = checkStillEligible(applicantId);

        if (!promotedBefore) {
            try {
                userClient.promote(applicantId, application.getTitle(), application.getSpecialty(), application.getYearsOfExperience());
            } catch (NotEligible refused) {
                if (!isAdvisor(applicantId)) {
                    throw refused;
                }
                log.info("User {} was already an advisor, approving application {} anyway", applicantId, id);
            }
        }

        AdvisorApplication approved = transactionTemplate.execute(status -> {
            AdvisorApplication locked = applicationRepository.findLockedById(id)
                    .orElseThrow(() -> new InformationNotFound("Application not found"));
            if (locked.getStatus() != KycStatus.PENDING) {
                log.warn("Application {} was {} by someone else after user {} was promoted", id, locked.getStatus(), applicantId);
                throw new NotEligible("This application was reviewed by someone else in the meantime");
            }
            locked.setStatus(KycStatus.APPROVED);
            locked.setReviewedBy(adminId);
            locked.setReviewedAt(LocalDateTime.now());
            return applicationRepository.save(locked);
        });

        UserSummary user = userOrFallback(applicantId);
        safely("Approval notification", () -> notificationPublisher.publish(applicantId, NotificationType.ADVISOR_APPLICATION_REVIEWED,
                "Your advisor application was approved. Log in again to use your advisor account."));
        safely("Approval audit", () -> auditRecorder.record(AuditAction.ADVISOR_APPLICATION_APPROVED, adminId, id,
                "Approved the advisor application of " + user.displayName()));
        log.info("Advisor application {} approved by admin {}; user {} promoted to advisor", id, adminId, applicantId);
        return toResponse(approved, certificateRepository.findByApplication(approved), user);
    }

    // the user must still be an active, non-blacklisted client. Returns true when they are already an advisor with a
    // profile, which only happens when an earlier approval promoted them but did not get to mark the application
    private boolean checkStillEligible(Long applicantId) {
        UserInfo applicant = userClient.getById(applicantId);
        boolean promotedBefore = false;
        if (!"CLIENT".equals(applicant.role())) {
            if (!"ADVISOR".equals(applicant.role()) || !isAdvisor(applicantId)) {
                throw new NotEligible("This account is no longer a client");
            }
            log.info("User {} is already an advisor, an earlier approval was interrupted", applicantId);
            promotedBefore = true;
        }
        if (applicant.blacklisted()) {
            throw new NotEligible("Blacklisted accounts cannot be promoted to advisor");
        }
        if (!"ACTIVE".equals(applicant.status())) {
            throw new NotEligible("This account is not active");
        }
        return promotedBefore;
    }

    private boolean isAdvisor(Long userId) {
        try {
            userClient.getAdvisorByUser(userId);
            return true;
        } catch (InformationNotFound e) {
            return false;
        }
    }

    // requires a reason, which is sent to the applicant
    public AdvisorApplicationResponse reject(Long id, String reason, Long adminId) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidRequest("A rejection reason is required");
        }
        AdvisorApplication rejected = transactionTemplate.execute(status -> {
            AdvisorApplication application = findPending(id);
            application.setStatus(KycStatus.REJECTED);
            application.setRejectionReason(reason);
            application.setReviewedBy(adminId);
            application.setReviewedAt(LocalDateTime.now());
            return applicationRepository.save(application);
        });

        Long applicantId = rejected.getUserId();
        UserSummary user = userOrFallback(applicantId);
        safely("Rejection notification", () -> notificationPublisher.publish(applicantId, NotificationType.ADVISOR_APPLICATION_REVIEWED,
                "Your advisor application was rejected. Reason: " + reason));
        safely("Rejection audit", () -> auditRecorder.record(AuditAction.ADVISOR_APPLICATION_REJECTED, adminId, id,
                "Rejected the advisor application of " + user.displayName()));
        log.info("Advisor application {} rejected by admin {}", id, adminId);
        return toResponse(rejected, certificateRepository.findByApplication(rejected), user);
    }

    public ApplicationCertificate getCertificate(Long certificateId) {
        return certificateRepository.findById(certificateId)
                .orElseThrow(() -> new InformationNotFound("Certificate not found"));
    }

    public Resource loadCertificate(ApplicationCertificate certificate) {
        return fileStorageService.load(certificate.getFilePath());
    }

    // the applicant uploads a new file for one certificate of a pending application. The certificate goes back to
    // PROCESSING and is checked again after the commit
    public AdvisorApplicationResponse replaceCertificate(Long applicantId, Long certificateId, MultipartFile file) {
        AdvisorApplication application = transactionTemplate.execute(status -> {
            ApplicationCertificate certificate = getCertificate(certificateId);
            if (!certificate.getApplication().getUserId().equals(applicantId)) {
                throw new InvalidRequest("This is not your certificate");
            }
            // an admin approving or rejecting at the same moment gets a version conflict instead of reviewing a changing application
            AdvisorApplication locked = applicationRepository.findLockedById(certificate.getApplication().getId())
                    .orElseThrow(() -> new InformationNotFound("Application not found"));
            if (locked.getStatus() != KycStatus.PENDING) {
                throw new InvalidRequest("Only certificates of a pending application can be replaced");
            }
            // the running check would save the old file's result onto the new file
            if (certificate.getStatus() == CertificateStatus.PROCESSING) {
                throw new InvalidRequest("This certificate is still being checked, try again in a moment");
            }
            if (file == null || file.isEmpty()) {
                throw new InvalidRequest("A certificate file is required");
            }
            if (file.getContentType() == null || !ALLOWED_CERTIFICATE_TYPES.contains(file.getContentType())) {
                throw new InvalidRequest("Certificates must be PDF, JPG or PNG files");
            }

            certificate.setFilePath(fileStorageService.store(file, "advisor_applications"));
            certificate.setOriginalFileName(file.getOriginalFilename());
            certificate.setContentType(file.getContentType());
            certificate.setFileSize(file.getSize());
            certificate.setStatus(CertificateStatus.PROCESSING);
            certificate.setDetectedType(null);
            certificate.setSha256(null);
            certificateRepository.save(certificate);
            return locked;
        });

        Long applicationId = application.getId();
        certificateProcessor.processAll(applicationId, applicantId, List.of(certificateId));
        UserSummary user = userOrFallback(applicantId);
        safely("Certificate audit", () -> auditRecorder.record(AuditAction.CERTIFICATE_REPLACED, applicantId, certificateId,
                user.displayName() + " uploaded a new file for a certificate in application " + applicationId));
        log.info("Certificate {} of application {} replaced by user {}", certificateId, applicationId, applicantId);
        return toResponse(application, certificateRepository.findByApplication(application), user);
    }

    private AdvisorApplication findPending(Long id) {
        AdvisorApplication application = applicationRepository.findById(id)
                .orElseThrow(() -> new InformationNotFound("Application not found"));
        if (application.getStatus() != KycStatus.PENDING) {
            throw new InvalidRequest("Only pending applications can be reviewed");
        }
        return application;
    }
}
