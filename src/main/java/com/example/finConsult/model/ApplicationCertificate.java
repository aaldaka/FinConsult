package com.example.finConsult.model;

import com.example.finConsult.model.enums.CertificateStatus;
import jakarta.persistence.*;
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
    private String detectedType; // validate type (avoid rewriting in browser)

    private String sha256; // hash files to avoid duplicates or another applicant uploading the same file

}
