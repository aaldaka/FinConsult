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
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

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

    private KycDocument findPending(Long id) {
        KycDocument doc = getDocument(id);
        if (doc.getStatus() != KycStatus.PENDING) {
            throw new InvalidRequest("Only pending documents can be reviewed");
        }
        return doc;
    }

    @Transactional
    public KycDocResponse submit(User user, MultipartFile file, DocumentType type) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequest("A file is required");
        }
        if (type == null) {
            throw new InvalidRequest("Document type is required");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new InvalidRequest("File is too large, max size 5 Mbs");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new InvalidRequest("Only JPEG, PNG, DOCX or PDF files are allowed");
        }

        // no spam uploading
        if (kycDocumentRepository.existsByUserAndDocumentTypeAndStatus(user, type, KycStatus.PENDING)) {
            throw new InvalidRequest("You already have a pending " + type + " document");
        }
        //kyc arent accessible
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
                "Submitted " + type);
        return mapper.toKyc(doc);
    }

    public List<KycDocResponse> getMyDocuments(User user) {
        return kycDocumentRepository.findByUser(user).stream().map(mapper::toKyc).toList();
    }

    public Page<KycDocResponse> getByStatus(KycStatus status, Pageable p, DocumentType docType) {
        Page<KycDocument> docs = (docType == null)
                ? kycDocumentRepository.findByStatus(status, p)
                : kycDocumentRepository.findByStatusAndDocumentType(status, docType, p);
        return docs.map(mapper::toKyc);
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
                "Approved " + doc.getDocumentType() + " for " + doc.getUser().getEmail());
        return mapper.toKyc(doc);
    }

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
                "Rejected " + doc.getDocumentType() + " for " + doc.getUser().getEmail());
        return mapper.toKyc(doc);
    }


    public KycDocument getDocument(Long id) {
        return kycDocumentRepository.findById(id)
                .orElseThrow(() -> new InformationNotFound("KYC document not found"));
    }

    public Resource loadFile(KycDocument doc) {
        return fileStorageService.load(doc.getFilePath());
    }
}