package com.example.finConsult.service;

import com.example.finConsult.event.NotificationCreatedEvent;
import com.example.finConsult.exception.InformationNotFound;
import com.example.finConsult.exception.InvalidRequest;
import com.example.finConsult.mapper.ResponseMapper;
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
    private final ResponseMapper mapper;

    public List<NotificationResponse> getMyNotifications(User user) {
        return notificationRepository.findByUser(user).stream().map(mapper::toNotification).toList();
    }

    public List<NotificationResponse> getMyUnreadNotifications(User user) {
        return notificationRepository.findByUserAndIsReadFalse(user).stream().map(mapper::toNotification).toList();
    }

    @Transactional
    public NotificationResponse markAsRead(Long notificationId, User user) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new InformationNotFound("Notification not found"));

        if (!notification.getUser().getId().equals(user.getId())) { //avoids an authenticated user entering other ppl' notif
            throw new InvalidRequest("This is not your notification");
        }

        notification.setRead(true);
        return mapper.toNotification(notificationRepository.save(notification));
    }

    @Transactional
    public Notification create(User user, NotificationType type, String message, Appointment related) {
        Notification notif = new Notification();
        notif.setUser(user);
        notif.setType(type);
        notif.setMessage(message);
        notif.setRelatedAppointment(related);
        notif = notificationRepository.save(notif);
        eventPublisher.publishEvent(new NotificationCreatedEvent(user.getId(), mapper.toNotification(notif)));
        return notif;
    }

}
