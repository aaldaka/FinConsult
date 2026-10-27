package com.example.finConsult.model;

import com.example.finConsult.model.enums.KycStatus;
import com.example.finConsult.model.enums.Specialty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

//advisor can be created or a client requests to be made an advisor
@Entity
@Getter
@Setter
@Table(name = "advisor_applications")
public class AdvisorApplication extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // the client requesting to become an advisor

    @Column(nullable = false)
    private String title; // proposed job title

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Specialty specialty;

    @Column(name = "years_of_experience")
    private int yearsOfExperience;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KycStatus status = KycStatus.PENDING; // same status as kyc

    @ManyToOne
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy; // which admin, auditing

    private LocalDateTime reviewedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Version
    @ColumnDefault("0")
    private Long version; //optimistic locks
}
