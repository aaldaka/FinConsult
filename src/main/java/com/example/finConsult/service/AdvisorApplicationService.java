package com.example.finConsult.service;

import com.example.finConsult.exception.NotEligible;
import com.example.finConsult.exception.InformationNotFound;
import com.example.finConsult.exception.InvalidRequest;
import com.example.finConsult.mapper.ResponseMapper;
import com.example.finConsult.model.*;
import com.example.finConsult.model.enums.*;
import com.example.finConsult.model.request.AdvisorApplicationRequest;
import com.example.finConsult.model.response.AdvisorApplicationResponse;
import com.example.finConsult.model.response.MyApplicationsResponse;
import com.example.finConsult.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdvisorApplicationService {

    private static final int MAX_APPLICATIONS = 3;
    private static final List<String> ALLOWED_CERTIFICATE_TYPES =
            List.of("application/pdf", "image/jpeg", "image/png");
    private static final List<DocumentType> IDENTITY_DOCUMENTS =
            List.of(DocumentType.NATIONAL_ID, DocumentType.PASSPORT);

    private final AdvisorApplicationRepository applicationRepository;
    private final ApplicationCertificateRepository certificateRepository;
    private final KycDocumentRepository kycDocumentRepository;
    private final AdvisorRepository advisorRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final ResponseMapper mapper;

    @Transactional
    public AdvisorApplicationResponse submit(User applicant, AdvisorApplicationRequest request, List<MultipartFile> certificates) {
        if (applicant.getRole() != UserRole.CLIENT) {
            log.warn("Advisor application rejected: user {} is not a client", applicant.getId());
            throw new NotEligible("Only clients can apply to become an advisor");
        }
        if (applicant.getUserStatus() != UserStatus.ACTIVE) {
            log.warn("Advisor application rejected: user {} is not active", applicant.getId());
            throw new NotEligible("Your account must be active to apply");
        }
        if (applicant.isBlacklisted()) {
            log.warn("Advisor application rejected: user {} is blacklisted", applicant.getId());
            throw new NotEligible("Blacklisted accounts cannot apply to become an advisor");
        }

        boolean hasIdentityKyc = IDENTITY_DOCUMENTS.stream().anyMatch(type ->
                kycDocumentRepository.existsByUserAndDocumentTypeAndStatus(applicant, type, KycStatus.APPROVED));
        if (!hasIdentityKyc) {
            log.warn("Advisor application rejected: user {} has no approved identity document", applicant.getId());
            throw new NotEligible("An approved national ID or passport is required before applying");
        }

        if (applicationRepository.existsByUserAndStatus(applicant, KycStatus.PENDING)) {
            log.warn("Advisor application rejected: user {} already has one pending", applicant.getId());
            throw new NotEligible("You already have an application awaiting review");
        }

        long used = applicationRepository.countByUser(applicant);
        if (used >= MAX_APPLICATIONS) {
            log.warn("Advisor application rejected: user {} used all {} attempts", applicant.getId(), MAX_APPLICATIONS);
            throw new NotEligible("You have used all " + MAX_APPLICATIONS + " application attempts");
        }

        if (certificates == null || certificates.isEmpty() || certificates.stream().allMatch(MultipartFile::isEmpty)) {
            throw new InvalidRequest("At least one certificate is required");
        }
        for (MultipartFile file : certificates) {
            String contentType = file.getContentType();
            if (contentType == null || !ALLOWED_CERTIFICATE_TYPES.contains(contentType)) {
                log.warn("Advisor application rejected: unsupported certificate type for user {}", applicant.getId());
                throw new InvalidRequest("Certificates must be PDF, JPG or PNG files");
            }
        }

        AdvisorApplication application = new AdvisorApplication();
        application.setUser(applicant);
        application.setTitle(request.getTitle());
        application.setSpecialty(request.getSpecialty() != null ? request.getSpecialty() : Specialty.GENERAL);
        application.setYearsOfExperience(request.getYearsOfExperience());
        application.setStatus(KycStatus.PENDING);
        application = applicationRepository.save(application);

        for (MultipartFile file : certificates) {
            String path = fileStorageService.store(file, "advisor_applications");
            ApplicationCertificate certificate = new ApplicationCertificate();
            certificate.setApplication(application);
            certificate.setFilePath(path);
            certificate.setOriginalFileName(file.getOriginalFilename());
            certificate.setContentType(file.getContentType());
            certificate.setFileSize(file.getSize());
            certificateRepository.save(certificate);
        }

        auditLogService.log(applicant, AuditAction.ADVISOR_APPLICATION_SUBMITTED, "AdvisorApplication",
                application.getId(), "Applied to become an advisor");
        log.info("Advisor application {} submitted by user {}", application.getId(), applicant.getId());

        return mapper.toApplication(application, certificateRepository.findByApplication(application));
    }

    public MyApplicationsResponse getMyApplications(User applicant) {
        List<AdvisorApplicationResponse> applications = applicationRepository.findByUser(applicant).stream()
                .map(a -> mapper.toApplication(a, certificateRepository.findByApplication(a)))
                .toList();
        return new MyApplicationsResponse(applications, applicationRepository.countByUser(applicant), MAX_APPLICATIONS);
    }

    public Page<AdvisorApplicationResponse> getByStatus(KycStatus status, Pageable pageable) {
        return applicationRepository.findByStatus(status, pageable)
                .map(a -> mapper.toApplication(a, certificateRepository.findByApplication(a)));
    }

    @Transactional
    public AdvisorApplicationResponse approve(Long id, User admin) {
        AdvisorApplication application = findPending(id);
        User applicant = application.getUser();

        if (applicant.getRole() != UserRole.CLIENT) {
            throw new NotEligible("This account is no longer a client");
        }
        if (applicant.isBlacklisted()) {
            throw new NotEligible("Blacklisted accounts cannot be promoted to advisor");
        }

        application.setStatus(KycStatus.APPROVED);
        application.setReviewedBy(admin);
        application.setReviewedAt(LocalDateTime.now());
        applicationRepository.save(application);

        Advisor advisor = new Advisor();
        advisor.setUser(applicant);
        advisor.setTitle(application.getTitle());
        advisor.setSpecialty(application.getSpecialty());
        advisor.setYearsOfExperience(application.getYearsOfExperience());
        advisor.setActive(true);
        advisorRepository.save(advisor);

        applicant.setRole(UserRole.ADVISOR);
        userRepository.save(applicant);

        notificationService.create(applicant, NotificationType.ADVISOR_APPLICATION_REVIEWED,
                "Your advisor application was approved. Log in again to use your advisor account.", null);
        auditLogService.log(admin, AuditAction.ADVISOR_APPLICATION_APPROVED, "AdvisorApplication", id,
                "Approved advisor application for user " + applicant.getId());
        log.info("Advisor application {} approved by admin {}; user {} promoted to advisor",
                id, admin.getId(), applicant.getId());

        return mapper.toApplication(application, certificateRepository.findByApplication(application));
    }

    @Transactional
    public AdvisorApplicationResponse reject(Long id, String reason, User admin) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidRequest("A rejection reason is required");
        }
        AdvisorApplication application = findPending(id);

        application.setStatus(KycStatus.REJECTED);
        application.setRejectionReason(reason);
        application.setReviewedBy(admin);
        application.setReviewedAt(LocalDateTime.now());
        applicationRepository.save(application);

        notificationService.create(application.getUser(), NotificationType.ADVISOR_APPLICATION_REVIEWED,
                "Your advisor application was rejected. Reason: " + reason, null);
        auditLogService.log(admin, AuditAction.ADVISOR_APPLICATION_REJECTED, "AdvisorApplication", id,
                "Rejected advisor application for user " + application.getUser().getId());
        log.info("Advisor application {} rejected by admin {}", id, admin.getId());

        return mapper.toApplication(application, certificateRepository.findByApplication(application));
    }

    public ApplicationCertificate getCertificate(Long certificateId) {
        return certificateRepository.findById(certificateId)
                .orElseThrow(() -> new InformationNotFound("Certificate not found"));
    }

    public Resource loadCertificate(ApplicationCertificate certificate) {
        return fileStorageService.load(certificate.getFilePath());
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
