package com.example.notificationservice.service;

import com.example.notificationservice.exception.InformationNotFound;
import com.example.notificationservice.exception.InvalidRequest;
import com.example.notificationservice.mapper.NotificationMapper;
import com.example.notificationservice.model.Notification;
import com.example.notificationservice.model.enums.NotificationType;
import com.example.notificationservice.model.response.NotificationResponse;
import com.example.notificationservice.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Stores and reads in-app notifications. Users and appointments are only ids here,
 * the data they came from lives in user-service and booking-service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final NotificationMapper mapper;

    /** @return all of the user's notifications */
    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications(Long userId) {
        return notificationRepository.findByUserId(userId).stream().map(mapper::toResponse).toList();
    }

    /** @return the user's unread notifications */
    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyUnreadNotifications(Long userId) {
        return notificationRepository.findByUserIdAndIsReadFalse(userId).stream().map(mapper::toResponse).toList();
    }

    /** Marks a notification read; only the recipient may do so. */
    @Transactional
    public NotificationResponse markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new InformationNotFound("Notification not found"));

        if (!notification.getUserId().equals(userId)) { //avoids an authenticated user entering other ppl' notif
            throw new InvalidRequest("This is not your notification");
        }

        notification.setRead(true);
        return mapper.toResponse(notificationRepository.save(notification));
    }

    /** Marks every unread notification of the user as read. @return how many were updated */
    @Transactional
    public int markAllAsRead(Long userId) {
        List<Notification> unread = notificationRepository.findByUserIdAndIsReadFalse(userId);
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
        return unread.size();
    }

    /** Saves a notification for a user. The appointment id is optional. */
    @Transactional
    public Notification create(Long userId, NotificationType type, String message, Long relatedAppointmentId) {
        Notification notif = new Notification();
        notif.setUserId(userId);
        notif.setType(type);
        notif.setMessage(message);
        notif.setRelatedAppointmentId(relatedAppointmentId);
        notif = notificationRepository.save(notif);
        log.debug("Notification created for user {} (type {})", userId, type);
        return notif;
    }
}
