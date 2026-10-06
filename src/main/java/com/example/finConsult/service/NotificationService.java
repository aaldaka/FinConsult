package com.example.finConsult.service;

import com.example.finConsult.event.NotificationCreatedEvent;
import com.example.finConsult.exception.InformationNotFound;
import com.example.finConsult.exception.InvalidRequest;
import com.example.finConsult.model.Appointment;
import com.example.finConsult.model.Notification;
import com.example.finConsult.model.User;
import com.example.finConsult.model.enums.NotificationType;
import com.example.finConsult.model.response.NotificationResponse;
import com.example.finConsult.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;

    public NotificationResponse toResponse(Notification notif) {
        return new NotificationResponse(
                notif.getId(),
                notif.getType(),
                notif.getMessage(),
                notif.isRead(),
                notif.getRelatedAppointment() == null ? null : notif.getRelatedAppointment().getId(),
                notif.getCreatedAt());
    }

    public List<Notification> getMyNotifications(User user) {
        return notificationRepository.findByUser(user);
    }

    public List<Notification> getMyUnreadNotifications(User user) {
        return notificationRepository.findByUserAndIsReadFalse(user);
    }

    @Transactional
    public Notification markAsRead(Long notificationId, User user) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new InformationNotFound("Notification not found"));

        if (!notification.getUser().getId().equals(user.getId())) { //avoids an authenticated user entering other ppl' notif
            throw new InvalidRequest("This is not your notification");
        }

        notification.setRead(true);
        return notificationRepository.save(notification);
    }
    @Transactional
    public Notification create(User user, NotificationType type, String message, Appointment related) {
        Notification notif = new Notification();
        notif.setUser(user);
        notif.setType(type);
        notif.setMessage(message);
        notif.setRelatedAppointment(related);
        notif = notificationRepository.save(notif);
        eventPublisher.publishEvent(new NotificationCreatedEvent(user.getId(), toResponse(notif)));
        return notif;
    }

}
