package com.example.finConsult.model.response;

import com.example.finConsult.model.enums.NotificationType;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class NotificationResponse {
    private Long id;
    private NotificationType type;
    private String message;
    private boolean read;
    private Long relatedAppointmentId;   // null when there's no appointment
    private LocalDateTime createdAt;
}