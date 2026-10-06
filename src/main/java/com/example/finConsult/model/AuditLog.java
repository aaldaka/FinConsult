package com.example.finConsult.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "actor_id", nullable = true) // who (can also be triggered from an event ie failed login)
    private User actor;

    private String action;

    @Column(name = "entity_type") // what was altered
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(length = 800)
    private String details;

    @CreationTimestamp
    @Column
    private LocalDateTime timestamp;
}