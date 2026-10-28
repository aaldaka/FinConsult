package com.example.finConsult.service;

import com.example.finConsult.exception.InformationNotFound;
import com.example.finConsult.exception.InvalidRequest;
import com.example.finConsult.mapper.ResponseMapper;
import com.example.finConsult.model.KycDocument;
import com.example.finConsult.model.User;
import com.example.finConsult.model.enums.AuditAction;
import com.example.finConsult.model.enums.DocumentType;
import com.example.finConsult.model.enums.KycStatus;
import com.example.finConsult.model.enums.NotificationType;
import com.example.finConsult.model.response.KycDocResponse;
import com.example.finConsult.repository.KycDocumentRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Handles KYC document submission and admin review (approve/reject), including
 * file validation and the duplicate-pending-submission guard.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KycService {

    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;
    private static final List<String> ALLOWED_TYPES = List.of("image/jpeg", "image/png", "application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final KycDocumentRepository kycDocumentRepository;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final ResponseMapper mapper;
    private final DocumentProcessor documentProcessor;

    /** @return the document, only if it's still PENDING */
    private KycDocument findPending(Long id) {
        KycDocument doc = getDocument(id);
        if (doc.getStatus() != KycStatus.PENDING) {
            throw new InvalidRequest("Only pending documents can be reviewed");
        }
        return doc;
    }

    @Transactional
    public KycDocResponse submit(User user, MultipartFile file, DocumentType type) {
        KycDocument doc = saveDocument(user, file, type);
        checkAfterCommit(user.getId(), List.of(doc.getId()));
        return mapper.toKyc(doc);
    }

    @Transactional
    public List<KycDocResponse> submitAll(User user, List<MultipartFile> files, List<DocumentType> types) {
        if (files == null || files.isEmpty() || types == null || files.size() != types.size()) {
            throw new InvalidRequest("Each file needs a document type");
        }
        if (new HashSet<>(types).size() != types.size()) {
            throw new InvalidRequest("Only one file per document type");
        }

        List<KycDocument> docs = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            docs.add(saveDocument(user, files.get(i), types.get(i)));
        }

        checkAfterCommit(user.getId(), docs.stream().map(KycDocument::getId).toList());
        return docs.stream().map(mapper::toKyc).toList();
    }

    // runs the background checks only once the documents are committed
    private void checkAfterCommit(Long userId, List<Long> documentIds) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                documentProcessor.processKycAll(userId, documentIds);
            }
        });
    }

    private void validateFile(User user, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequest("A file is required");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            log.warn("KYC upload rejected: file too large for user {}", user.getId());
            throw new InvalidRequest("File is too large, max size 5 Mbs");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            log.warn("KYC upload rejected: unsupported type for user {}", user.getId());
            throw new InvalidRequest("Only JPEG, PNG, DOCX or PDF files are allowed");
        }
    }

    private KycDocument saveDocument(User user, MultipartFile file, DocumentType type) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequest("A file is required");
        }
        if (type == null) {
            throw new InvalidRequest("Document type is required");
        }
        validateFile(user, file);
        String contentType = file.getContentType();

        // no spam uploading
        if (kycDocumentRepository.existsByUserAndDocumentTypeAndStatus(user, type, KycStatus.PENDING)) {
            throw new InvalidRequest("You already have a pending " + type + " document");
        }
        String path = fileStorageService.store(file, "kyc_documents");

        KycDocument doc = new KycDocument();
        doc.setUser(user);
        doc.setDocumentType(type);
        doc.setFilePath(path);
        doc.setOriginalFileName(file.getOriginalFilename());
        doc.setContentType(contentType);
        doc.setFileSize(file.getSize());
        doc = kycDocumentRepository.save(doc);   // status defaults to PENDING

        auditLogService.log(user, AuditAction.KYC_SUBMITTED, "KycDocument", doc.getId(),
                mapper.nameOf(user) + " submitted a " + type + " document");
        log.info("KYC document {} submitted by user {}", doc.getId(), user.getId());
        return doc;
    }

    /**
     * Client uploads a new file for one of their documents. It goes back to PENDING with the
     * review and check results cleared, and is checked again after the commit.
     */
    @Transactional
    public KycDocResponse replaceFile(User user, Long id, MultipartFile file) {
        KycDocument doc = getDocument(id);
        if (!doc.getUser().getId().equals(user.getId())) {
            throw new InvalidRequest("This is not your document");
        }
        // the running check would save the old file's result onto the new file
        if (doc.getStatus() == KycStatus.PENDING && doc.getDetectedType() == null && doc.getFlagReason() == null) {
            throw new InvalidRequest("This document is still being checked, try again in a moment");
        }
        if (doc.getStatus() != KycStatus.PENDING
                && kycDocumentRepository.existsByUserAndDocumentTypeAndStatus(user, doc.getDocumentType(), KycStatus.PENDING)) {
            throw new InvalidRequest("You already have a pending " + doc.getDocumentType() + " document, replace that one instead");
        }
        validateFile(user, file);

        doc.setFilePath(fileStorageService.store(file, "kyc_documents"));
        doc.setOriginalFileName(file.getOriginalFilename());
        doc.setContentType(file.getContentType());
        doc.setFileSize(file.getSize());
        doc.setStatus(KycStatus.PENDING);
        doc.setReviewedBy(null);
        doc.setReviewedAt(null);
        doc.setRejectionReason(null);
        doc.setDetectedType(null);
        doc.setSha256(null);
        doc.setFlagReason(null);
        doc = kycDocumentRepository.save(doc);   // @Version: an admin reviewing it at the same moment gets a 409

        auditLogService.log(user, AuditAction.KYC_RESUBMITTED, "KycDocument", id,
                mapper.nameOf(user) + " uploaded a new " + doc.getDocumentType() + " file");
        log.info("KYC document {} replaced by user {}", id, user.getId());

        checkAfterCommit(user.getId(), List.of(id));
        return mapper.toKyc(doc);
    }

    public List<KycDocResponse> getMyDocuments(User user) {
        return kycDocumentRepository.findByUser(user).stream().map(mapper::toKyc).toList();
    }

    public Page<KycDocResponse> getByStatus(KycStatus status, Pageable p, DocumentType docType) {
        Page<KycDocument> docs = (docType == null)
                ? kycDocumentRepository.findByStatus(status, p)
                : kycDocumentRepository.findByStatusAndDocumentType(status, docType, p);
        return docs.map(mapper::toKycForAdmin);
    }

    @Transactional
    public KycDocResponse approve(Long id, User admin) {
        KycDocument doc = findPending(id);
        doc.setStatus(KycStatus.APPROVED);
        doc.setReviewedBy(admin);
        doc.setReviewedAt(LocalDateTime.now());
        kycDocumentRepository.save(doc);

        notificationService.create(doc.getUser(), NotificationType.KYC_REVIEWED,
                "Your " + doc.getDocumentType() + " document has been approved.", null);

        auditLogService.log(admin, AuditAction.KYC_APPROVED, "KycDocument", id,
                "Approved " + doc.getDocumentType() + " for " + mapper.nameOf(doc.getUser()));
        log.info("KYC document {} approved by admin {}", id, admin.getId());
        return mapper.toKycForAdmin(doc);
    }

    /** Requires a rejection reason. */
    @Transactional
    public KycDocResponse reject(Long id, String reason, User admin) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidRequest("A rejection reason is required");
        }
        KycDocument doc = findPending(id);
        doc.setStatus(KycStatus.REJECTED);
        doc.setRejectionReason(reason);
        doc.setReviewedBy(admin);
        doc.setReviewedAt(LocalDateTime.now());
        kycDocumentRepository.save(doc);

        notificationService.create(doc.getUser(), NotificationType.KYC_REVIEWED,
                "Your " + doc.getDocumentType() + " document was rejected. Reason: " + reason, null);

        auditLogService.log(admin, AuditAction.KYC_REJECTED, "KycDocument", id,
                "Rejected " + doc.getDocumentType() + " for " + mapper.nameOf(doc.getUser()));
        log.info("KYC document {} rejected by admin {}", id, admin.getId());
        return mapper.toKycForAdmin(doc);
    }


    public KycDocument getDocument(Long id) {
        return kycDocumentRepository.findById(id)
                .orElseThrow(() -> new InformationNotFound("KYC document not found"));
    }

    public Resource loadFile(KycDocument doc) {
        return fileStorageService.load(doc.getFilePath());
    }
}