package com.example.finConsult.service;

import com.example.finConsult.exception.InformationNotFound;
import com.example.finConsult.model.AdvisorApplication;
import com.example.finConsult.model.ApplicationCertificate;
import com.example.finConsult.model.KycDocument;
import com.example.finConsult.model.User;
import com.example.finConsult.model.enums.CertificateStatus;
import com.example.finConsult.model.enums.KycStatus;
import com.example.finConsult.model.enums.NotificationType;
import com.example.finConsult.model.enums.UserRole;
import com.example.finConsult.repository.ApplicationCertificateRepository;
import com.example.finConsult.repository.KycDocumentRepository;
import com.example.finConsult.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Checks uploaded KYC documents and certificates on the doc-worker pool: real file type,
 * SHA-256 hash for duplicates, and a VirusTotal lookup. Flagged files notify the admins.
 */
@Slf4j
@Component
public class DocumentProcessor {

    private record FileInfo(String type, String hash) {}
    private final ApplicationCertificateRepository certificateRepository;
    private final KycDocumentRepository kycDocumentRepository;
    private final FileStorageService fileStorageService;
    private final NotificationStreamService streamService;
    private final Executor executor;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final VirusTotalService virusTotalService;

    public DocumentProcessor(ApplicationCertificateRepository certificateRepository, KycDocumentRepository kycDocumentRepository, FileStorageService fileStorageService, NotificationStreamService streamService,
                             @Qualifier("docExecutor") Executor executor, UserRepository userRepository, NotificationService notificationService, VirusTotalService virusTotalService) {
        this.certificateRepository = certificateRepository;
        this.kycDocumentRepository = kycDocumentRepository;
        this.fileStorageService = fileStorageService;
        this.streamService = streamService;
        this.executor = executor;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.virusTotalService = virusTotalService;
    }

    /** One task per document on the pool, when all are done the client gets a KYC_CHECKED event. */
    public CompletableFuture<Void> processKycAll(Long userId, List<Long> documentIds) {
        log.info("Submitting {} KYC checks for user {}", documentIds.size(), userId);

        List<CompletableFuture<Void>> futures = documentIds.stream()
                .map(id -> CompletableFuture.runAsync(() -> checkKyc(id), executor))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    streamService.push(userId,
                            Map.of("type", "KYC_CHECKED", "documentIds", documentIds));

                    long flagged = kycDocumentRepository.findAllById(documentIds).stream()
                            .filter(d -> d.getFlagReason() != null)
                            .count();
                    if (flagged > 0) {
                        notifyAdmins(flagged + " KYC document(s) from user " + userId
                                + " were flagged. A review is needed");
                    }
                })
                .exceptionally(e -> {
                    log.error("Document checks failed", e);
                    return null;
                });
    }

    private void checkKyc(Long documentId) {
        log.info("Checking KYC document {} on {}", documentId, Thread.currentThread().getName());

        KycDocument doc = kycDocumentRepository.findById(documentId)
                .orElseThrow(() -> new InformationNotFound("KYC document not found"));

        try {
            FileInfo info = inspect(doc.getFilePath());
            doc.setDetectedType(info.type());
            doc.setSha256(info.hash());
            doc = saveCheckResult(documentId, doc);   // hash saved first so a parallel check can see it

            if (info.type() == null) {
                doc.setFlagReason("File content is not a real PDF, JPG, PNG or DOCX");
            } else if (kycDocumentRepository.existsBySha256AndUserNot(info.hash(), doc.getUser())) {
                doc.setFlagReason("Same file was uploaded by another account");
            } else if (virusTotalService.lookup(info.hash()).status() == ScanResult.Status.MALICIOUS) {
                doc.setFlagReason("Matches malware");
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Could not read KYC document {}: {}", documentId, e.getMessage());
            doc.setFlagReason("Could not read the file");
        }

        doc = saveCheckResult(documentId, doc);
        log.info("KYC document {} checked, flag: {}", documentId, doc.getFlagReason());
    }

    // One task per file on the pool; when all are done, tell the applicant over SSE
    public CompletableFuture<Void> processAll(Long applicationId, Long applicantId, List<Long> certificateIds) {
        log.info("Submitting {} certificate checks for application {}", certificateIds.size(), applicationId);

        List<CompletableFuture<CertificateStatus>> futures = certificateIds.stream()
                .map(id -> CompletableFuture.supplyAsync(() -> check(id), executor))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    streamService.push(applicantId,
                            Map.of("type", "CERTIFICATES_CHECKED", "applicationId", applicationId));

                    long flagged = certificateRepository.findAllById(certificateIds).stream()
                            .filter(c -> c.getStatus() == CertificateStatus.FLAGGED)
                            .count();
                    if (flagged > 0) {
                        notifyAdmins(flagged + " certificate(s) in advisor application " + applicationId
                                + " were flagged. A review is required");
                    }
                })
                .exceptionally(e -> {
                    log.error("Document checks failed", e);
                    return null;
                });
    }

    // runs on a doc-worker thread
    private CertificateStatus check(Long certificateId) {
        log.info("Checking certificate {} on {}", certificateId, Thread.currentThread().getName());

        ApplicationCertificate cert = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new InformationNotFound("Certificate not found"));

        try {
            FileInfo info = inspect(cert.getFilePath());
            cert.setDetectedType(info.type());
            cert.setSha256(info.hash());
            certificateRepository.save(cert); // save hash and check immedietly to detect any duplicates

            AdvisorApplication application = cert.getApplication();
            boolean duplicate = certificateRepository.existsBySha256AndApplication_UserNot(info.hash(), application.getUser()) || certificateRepository.existsBySha256AndApplicationAndIdNot(info.hash(), application, cert.getId());
            boolean malware = info.type() != null && !duplicate && virusTotalService.lookup(info.hash()).status() == ScanResult.Status.MALICIOUS;
            cert.setStatus(info.type() != null && !duplicate && !malware ? CertificateStatus.VALID : CertificateStatus.FLAGGED);
        } catch (IOException | RuntimeException e) {
            log.warn("Could not check certificate {}: {}", certificateId, e.getMessage());
            cert.setStatus(CertificateStatus.FLAGGED);
        }

        certificateRepository.save(cert);
        log.info("Certificate {} -> {}", certificateId, cert.getStatus());
        return cert.getStatus();
    }

    // check the type and hash and save it
    private FileInfo inspect(String filePath) throws IOException {
        byte[] bytes;
        try (InputStream in = fileStorageService.load(filePath).getInputStream()) {
            bytes = in.readAllBytes();
        }
        return new FileInfo(detectType(bytes), sha256(bytes));
    }

    // additional check to detected changed extensions
    private String detectType(byte[] bytes) {
        if (startsWith(bytes, 0x25, 0x50, 0x44, 0x46)) return "PDF";
        if (startsWith(bytes, 0x89, 0x50, 0x4E, 0x47)) return "PNG";
        if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) return "JPG";
        if (startsWith(bytes, 0x50, 0x4B, 0x03, 0x04)) return "DOCX";
        return null;
    }

    private boolean startsWith(byte[] bytes, int... signature) {
        if (bytes.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[i] & 0xFF) != signature[i]) return false;
        }
        return true;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    // notify admins of a batch of flagged ones
    private void notifyAdmins(String message) {
        for (User admin : userRepository.findByRole(UserRole.ADMIN)) {
            notificationService.create(admin, NotificationType.DOCUMENT_FLAGGED, message, null);
        }
    }

    KycDocument saveCheckResult(Long documentId, KycDocument doc) {
        // also needed without a conflict: the approval may have been picked up by the earlier hash save
        sendBackIfApprovedAndFlagged(documentId, doc);
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return kycDocumentRepository.save(doc);
            } catch (ObjectOptimisticLockingFailureException e) {
                log.warn("KYC document {} changed while being checked, retrying ({})", documentId, attempt);
                KycDocument fresh = kycDocumentRepository.findById(documentId).orElseThrow();
                fresh.setDetectedType(doc.getDetectedType());
                fresh.setSha256(doc.getSha256());
                fresh.setFlagReason(doc.getFlagReason());
                sendBackIfApprovedAndFlagged(documentId, fresh);
                doc = fresh;
            } catch (DataIntegrityViolationException e) {
                // the client already has another pending document of this type, so keep the approval and only record the flag
                log.warn("KYC document {} could not go back to pending, keeping the approval with the flag", documentId);
                KycDocument fresh = kycDocumentRepository.findById(documentId).orElseThrow();
                fresh.setDetectedType(doc.getDetectedType());
                fresh.setSha256(doc.getSha256());
                fresh.setFlagReason(doc.getFlagReason());
                doc = fresh;
            }
        }
        log.error("Could not save the check result for KYC document {}", documentId);
        return doc;
    }

    // approved a moment ago, but the check flagged it: send it back to review
    private void sendBackIfApprovedAndFlagged(Long documentId, KycDocument doc) {
        if (doc.getStatus() == KycStatus.APPROVED && doc.getFlagReason() != null) {
            doc.setStatus(KycStatus.PENDING);
            doc.setReviewedBy(null);
            doc.setReviewedAt(null);
            log.warn("KYC document {} was approved but the check flagged it, sent back to review", documentId);
        }
    }

}