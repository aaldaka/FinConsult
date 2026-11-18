package com.example.notificationservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// one entry of the shared audit_logs table. The services send the entries as AUDIT events and this service
// writes them. actor_id is only a number here (null for something the system did on its own), and timestamp is when
// the change happened, taken from the event, not when it was written
@Entity
@Getter
@Setter
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id")
    private Long actorId;

    private String action;

    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(length = 800)
    private String details;

    @Column(nullable = false)
    private LocalDateTime timestamp;
}
