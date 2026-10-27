package com.example.finConsult.model;

import com.example.finConsult.model.enums.NotificationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    private String message;

    @Column
    private boolean isRead = false;

    @ManyToOne
    @JoinColumn(name = "related_appointment_id")
    private Appointment relatedAppointment;

    @CreationTimestamp
    @Column
    private LocalDateTime createdAt;
}