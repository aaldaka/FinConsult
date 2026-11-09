package com.example.notificationservice.model;

import com.example.notificationservice.model.enums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "notifications", indexes = @Index(name = "idx_notifications_user_id", columnList = "user_id"))
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // id of the user in the main app, only a number: this service has no users table and no foreign keys
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    // longer than the default 255 because a review reason can be that long on its own
    @Column(length = 1000)
    private String message;

    @Column
    private boolean isRead = false;

    // id of the appointment in the main app, null when the notification is not about one
    @Column(name = "related_appointment_id")
    private Long relatedAppointmentId;

    @CreationTimestamp
    @Column
    private LocalDateTime createdAt;
}
