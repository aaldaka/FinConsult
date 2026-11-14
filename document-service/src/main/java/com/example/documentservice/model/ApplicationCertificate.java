package com.example.documentservice.model;

import com.example.documentservice.model.enums.CertificateStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "application_certificates")
public class ApplicationCertificate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private AdvisorApplication application;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "original_file_name")
    private String originalFileName;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "file_size")
    private long fileSize;

    @Enumerated(EnumType.STRING)
    private CertificateStatus status = CertificateStatus.PROCESSING;

    @Column(name = "detected_type")
    private String detectedType; // the real type from the file's bytes, not the client's content type

    private String sha256; // finds the same file uploaded twice or by another applicant
}
