package com.example.documentservice.service;

import com.example.documentservice.client.UserClient;
import com.example.documentservice.client.UserSummary;
import com.example.documentservice.exception.InformationNotFound;
import com.example.documentservice.exception.InvalidRequest;
import com.example.documentservice.integration.AuditRecorder;
import com.example.documentservice.integration.NotificationPublisher;
import com.example.documentservice.model.KycDocument;
import com.example.documentservice.model.enums.AuditAction;
import com.example.documentservice.model.enums.DocumentType;
import com.example.documentservice.model.enums.KycStatus;
import com.example.documentservice.model.enums.NotificationType;
import com.example.documentservice.model.response.KycDocResponse;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;

// KYC document submission and admin review. The calls to the user service and the stubs never run inside a database
// transaction: lookups a list needs happen before it, and the names, notifications, audit entries and the start of
// the background check happen after it has committed
@Slf4j
@Service
public class KycService {

    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;
    private static final List<String> ALLOWED_TYPES = List.of("image/jpeg", "image/png", "application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final KycDocumentRepository kycDocumentRepository;
    private final FileStorageService fileStorageService;
    private final DocumentProcessor documentProcessor;
    private final UserClient userClient;
    private final NotificationPublisher notificationPublisher;
    private final AuditRecorder auditRecorder;
    private final TransactionTemplate transactionTemplate;

    public KycService(KycDocumentRepository kycDocumentRepository, FileStorageService fileStorageService,
                      DocumentProcessor documentProcessor, UserClient userClient, NotificationPublisher notificationPublisher,
                      AuditRecorder auditRecorder, PlatformTransactionManager transactionManager) {
        this.kycDocumentRepository = kycDocumentRepository;
        this.fileStorageService = fileStorageService;
        this.documentProcessor = documentProcessor;
        this.userClient = userClient;
        this.notificationPublisher = notificationPublisher;
        this.auditRecorder = auditRecorder;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

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

    private KycDocResponse toResponse(KycDocument d, UserSummary user, boolean forAdmin) {
        return new KycDocResponse(
                d.getId(), user == null ? null : user.email(), d.getDocumentType(),
                d.getOriginalFileName(), d.getFileSize(), d.getStatus(),
                d.getRejectionReason(), d.getCreatedAt(), d.getReviewedAt(),
                d.getDetectedType(), d.getFlagReason() != null, forAdmin ? d.getFlagReason() : null);
    }

    // the document, only if it's still PENDING
    private KycDocument findPending(Long id) {
        KycDocument doc = getDocument(id);
        if (doc.getStatus() != KycStatus.PENDING) {
            throw new InvalidRequest("Only pending documents can be reviewed");
        }
        return doc;
    }

    // saves one document as PENDING and checks it in the background after the commit
    public KycDocResponse submit(Long userId, MultipartFile file, DocumentType type) {
        KycDocument doc = transactionTemplate.execute(status -> saveDocument(userId, file, type));
        return afterSubmit(userId, List.of(doc)).get(0);
    }

    // saves several documents (one per type) in one transaction, then checks them in parallel after the commit
    public List<KycDocResponse> submitAll(Long userId, List<MultipartFile> files, List<DocumentType> types) {
        if (files == null || files.isEmpty() || types == null || files.size() != types.size()) {
            throw new InvalidRequest("Each file needs a document type");
        }
        if (new HashSet<>(types).size() != types.size()) {
            throw new InvalidRequest("Only one file per document type");
        }
        List<KycDocument> docs = transactionTemplate.execute(status -> {
            List<KycDocument> saved = new ArrayList<>();
            for (int i = 0; i < files.size(); i++) {
                saved.add(saveDocument(userId, files.get(i), types.get(i)));
            }
            return saved;
        });
        return afterSubmit(userId, docs);
    }

    private List<KycDocResponse> afterSubmit(Long userId, List<KycDocument> docs) {
        UserSummary user = userOrFallback(userId);
        for (KycDocument doc : docs) {
            safely("Submission audit", () -> auditRecorder.record(AuditAction.KYC_SUBMITTED, userId, doc.getId(),
                    user.displayName() + " submitted a " + doc.getDocumentType() + " document"));
            log.info("KYC document {} submitted by user {}", doc.getId(), userId);
        }
        documentProcessor.processKycAll(userId, docs.stream().map(KycDocument::getId).toList());
        return docs.stream().map(d -> toResponse(d, user, false)).toList();
    }

    private void validateFile(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequest("A file is required");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            log.warn("KYC upload rejected: file too large for user {}", userId);
            throw new InvalidRequest("File is too large, max size 5 Mbs");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            log.warn("KYC upload rejected: unsupported type for user {}", userId);
            throw new InvalidRequest("Only JPEG, PNG, DOCX or PDF files are allowed");
        }
    }

    private KycDocument saveDocument(Long userId, MultipartFile file, DocumentType type) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequest("A file is required");
        }
        if (type == null) {
            throw new InvalidRequest("Document type is required");
        }
        validateFile(userId, file);
        // no spam uploading
        if (kycDocumentRepository.existsByUserIdAndDocumentTypeAndStatus(userId, type, KycStatus.PENDING)) {
            throw new InvalidRequest("You already have a pending " + type + " document");
        }
        String path = fileStorageService.store(file, "kyc_documents");

        KycDocument doc = new KycDocument();
        doc.setUserId(userId);
        doc.setDocumentType(type);
        doc.setFilePath(path);
        doc.setOriginalFileName(file.getOriginalFilename());
        doc.setContentType(file.getContentType());
        doc.setFileSize(file.getSize());
        return kycDocumentRepository.save(doc);   // status defaults to PENDING
    }

    // the client uploads a new file for one of their documents. It goes back to PENDING with the review and check
    // results cleared, and is checked again after the commit
    public KycDocResponse replaceFile(Long userId, Long id, MultipartFile file) {
        KycDocument replaced = transactionTemplate.execute(status -> {
            KycDocument doc = getDocument(id);
            if (!doc.getUserId().equals(userId)) {
                throw new InvalidRequest("This is not your document");
            }
            // the running check would save the old file's result onto the new file
            if (doc.getStatus() == KycStatus.PENDING && doc.getDetectedType() == null && doc.getFlagReason() == null) {
                throw new InvalidRequest("This document is still being checked, try again in a moment");
            }
            if (doc.getStatus() != KycStatus.PENDING
                    && kycDocumentRepository.existsByUserIdAndDocumentTypeAndStatus(userId, doc.getDocumentType(), KycStatus.PENDING)) {
                throw new InvalidRequest("You already have a pending " + doc.getDocumentType() + " document, replace that one instead");
            }
            validateFile(userId, file);

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
            return kycDocumentRepository.save(doc);   // @Version: an admin reviewing it at the same moment gets a 409
        });

        UserSummary user = userOrFallback(userId);
        safely("Resubmission audit", () -> auditRecorder.record(AuditAction.KYC_RESUBMITTED, userId, id,
                user.displayName() + " uploaded a new " + replaced.getDocumentType() + " file"));
        log.info("KYC document {} replaced by user {}", id, userId);
        documentProcessor.processKycAll(userId, List.of(id));
        return toResponse(replaced, user, false);
    }

    // the user service is asked before the list is built: if it is down this is a 503, nothing has changed
    public List<KycDocResponse> getMyDocuments(Long userId) {
        List<KycDocument> docs = kycDocumentRepository.findByUserId(userId);
        if (docs.isEmpty()) {
            return List.of();
        }
        UserSummary user = userClient.lookup(List.of(userId)).get(userId);
        return docs.stream().map(d -> toResponse(d, user, false)).toList();
    }

    // one call to the user service for the whole page, not one per row
    public Page<KycDocResponse> getByStatus(KycStatus status, Pageable p, DocumentType docType) {
        Page<KycDocument> docs = (docType == null)
                ? kycDocumentRepository.findByStatus(status, p)
                : kycDocumentRepository.findByStatusAndDocumentType(status, docType, p);
        Map<Long, UserSummary> users = docs.isEmpty() ? Map.of()
                : userClient.lookup(docs.stream().map(KycDocument::getUserId).distinct().toList());
        return docs.map(d -> toResponse(d, users.get(d.getUserId()), true));
    }

    // approves a pending document. @Version makes a concurrent review or a background check that saved in the meantime
    // fail with a conflict instead of being overwritten. Not blocked while the check runs: a flag found after the
    // approval sends the document back to review (see DocumentProcessor)
    public KycDocResponse approve(Long id, Long adminId) {
        KycDocument doc = transactionTemplate.execute(status -> {
            KycDocument pending = findPending(id);
            pending.setStatus(KycStatus.APPROVED);
            pending.setReviewedBy(adminId);
            pending.setReviewedAt(LocalDateTime.now());
            return kycDocumentRepository.save(pending);
        });

        UserSummary owner = userOrFallback(doc.getUserId());
        safely("Approval notification", () -> notificationPublisher.publish(doc.getUserId(), NotificationType.KYC_REVIEWED,
                "Your " + doc.getDocumentType() + " document has been approved."));
        safely("Approval audit", () -> auditRecorder.record(AuditAction.KYC_APPROVED, adminId, id,
                "Approved " + doc.getDocumentType() + " for " + owner.displayName()));
        log.info("KYC document {} approved by admin {}", id, adminId);
        return toResponse(doc, owner, true);
    }

    // requires a rejection reason
    public KycDocResponse reject(Long id, String reason, Long adminId) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidRequest("A rejection reason is required");
        }
        KycDocument doc = transactionTemplate.execute(status -> {
            KycDocument pending = findPending(id);
            pending.setStatus(KycStatus.REJECTED);
            pending.setRejectionReason(reason);
            pending.setReviewedBy(adminId);
            pending.setReviewedAt(LocalDateTime.now());
            return kycDocumentRepository.save(pending);
        });

        UserSummary owner = userOrFallback(doc.getUserId());
        safely("Rejection notification", () -> notificationPublisher.publish(doc.getUserId(), NotificationType.KYC_REVIEWED,
                "Your " + doc.getDocumentType() + " document was rejected. Reason: " + reason));
        safely("Rejection audit", () -> auditRecorder.record(AuditAction.KYC_REJECTED, adminId, id,
                "Rejected " + doc.getDocumentType() + " for " + owner.displayName()));
        log.info("KYC document {} rejected by admin {}", id, adminId);
        return toResponse(doc, owner, true);
    }

    public KycDocument getDocument(Long id) {
        return kycDocumentRepository.findById(id)
                .orElseThrow(() -> new InformationNotFound("KYC document not found"));
    }

    public Resource loadFile(KycDocument doc) {
        return fileStorageService.load(doc.getFilePath());
    }
}
