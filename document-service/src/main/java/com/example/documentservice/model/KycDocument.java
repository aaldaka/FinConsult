package com.example.documentservice.model;

import com.example.documentservice.model.enums.DocumentType;
import com.example.documentservice.model.enums.KycStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

// the users live in the user service, so the owner and the reviewer are plain ids on the same columns as before
@Entity
@Getter
@Setter
@Table(name = "kyc_documents")
public class KycDocument extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type")
    private DocumentType documentType;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "original_file_name")
    private String originalFileName;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "file_size")
    private long fileSize;

    @Enumerated(EnumType.STRING)
    private KycStatus status = KycStatus.PENDING;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "detected_type")
    private String detectedType;

    private String sha256;

    @Column(name = "flag_reason")
    private String flagReason;

    @Version
    @ColumnDefault("0")
    private Long version; // two admins approve/reject the same doc, or a review races the background check
}
