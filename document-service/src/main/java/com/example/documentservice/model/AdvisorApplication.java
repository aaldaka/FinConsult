package com.example.documentservice.model;

import com.example.documentservice.model.enums.KycStatus;
import com.example.documentservice.model.enums.Specialty;
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

// a client asking to become an advisor. The applicant and the reviewing admin are plain ids on the same columns as before
@Entity
@Getter
@Setter
@Table(name = "advisor_applications")
public class AdvisorApplication extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId; // the client requesting to become an advisor

    @Column(nullable = false)
    private String title; // proposed job title

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Specialty specialty;

    @Column(name = "years_of_experience")
    private int yearsOfExperience;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KycStatus status = KycStatus.PENDING; // same statuses as kyc

    @Column(name = "reviewed_by")
    private Long reviewedBy; // which admin

    private LocalDateTime reviewedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Version
    @ColumnDefault("0")
    private Long version; // approve and reject at the same moment
}
