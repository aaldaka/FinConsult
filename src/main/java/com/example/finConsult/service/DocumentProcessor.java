package com.example.finConsult.service;

import com.example.finConsult.exception.InformationNotFound;
import com.example.finConsult.model.ApplicationCertificate;
import com.example.finConsult.model.KycDocument;
import com.example.finConsult.model.enums.CertificateStatus;
import com.example.finConsult.repository.ApplicationCertificateRepository;
import com.example.finConsult.repository.KycDocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
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

@Slf4j
@Component
public class DocumentProcessor {

    private record FileInfo(String type, String hash) {}
    private final ApplicationCertificateRepository certificateRepository;
    private final KycDocumentRepository kycDocumentRepository;
    private final FileStorageService fileStorageService;
    private final NotificationStreamService streamService;
    private final Executor executor;

    public DocumentProcessor(ApplicationCertificateRepository certificateRepository, KycDocumentRepository kycDocumentRepository, FileStorageService fileStorageService, NotificationStreamService streamService,
                             @Qualifier("docExecutor") Executor executor) {
        this.certificateRepository = certificateRepository;
        this.kycDocumentRepository = kycDocumentRepository;
        this.fileStorageService = fileStorageService;
        this.streamService = streamService;
        this.executor = executor;
    }

    public CompletableFuture<Void> processKycAll(Long userId, List<Long> documentIds) {
        log.info("Submitting {} KYC checks for user {}", documentIds.size(), userId);

        List<CompletableFuture<Void>> futures = documentIds.stream()
                .map(id -> CompletableFuture.runAsync(() -> checkKyc(id), executor))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> streamService.push(userId,
                        Map.of("type", "KYC_CHECKED", "documentIds", documentIds)));
    }

    private void checkKyc(Long documentId) {
        log.info("Checking KYC document {} on {}", documentId, Thread.currentThread().getName());

        KycDocument doc = kycDocumentRepository.findById(documentId)
                .orElseThrow(() -> new InformationNotFound("KYC document not found"));

        try {
            FileInfo info = inspect(doc.getFilePath());
            doc.setDetectedType(info.type());
            doc.setSha256(info.hash());

            if (info.type() == null) {
                doc.setFlagReason("File content is not a real PDF, JPG, PNG or DOCX");
            } else if (kycDocumentRepository.existsBySha256AndUserNot(info.hash(), doc.getUser())) {
                doc.setFlagReason("Same file was uploaded by another account");
            }
        } catch (IOException e) {
            log.warn("Could not read KYC document {}: {}", documentId, e.getMessage());
            doc.setFlagReason("Could not read the file");
        }

        kycDocumentRepository.save(doc);
        log.info("KYC document {} checked, flag: {}", documentId, doc.getFlagReason());
    }

    // One task per file on the pool; when all are done, tell the applicant over SSE
    public CompletableFuture<Void> processAll(Long applicationId, Long applicantId, List<Long> certificateIds) {
        log.info("Submitting {} certificate checks for application {}", certificateIds.size(), applicationId);

        List<CompletableFuture<CertificateStatus>> futures = certificateIds.stream()
                .map(id -> CompletableFuture.supplyAsync(() -> check(id), executor))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> streamService.push(applicantId,
                        Map.of("type", "CERTIFICATES_CHECKED", "applicationId", applicationId)));
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

            boolean duplicate = certificateRepository.existsBySha256AndIdNot(info.hash(), cert.getId());
            cert.setStatus(info.type() != null && !duplicate ? CertificateStatus.VALID : CertificateStatus.FLAGGED);
        } catch (IOException e) {
            log.warn("Could not read certificate {}: {}", certificateId, e.getMessage());
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
            if ((bytes[i] & 0xFFcl) != signature[i]) return false;
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
}