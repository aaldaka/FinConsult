package com.example.notificationservice.controller;

import com.example.notificationservice.model.response.MessageResponse;
import com.example.notificationservice.model.response.NotificationResponse;
import com.example.notificationservice.security.AuthenticatedUser;
import com.example.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * In-app notification listing and read-state endpoints. The user id comes from the token.
 * The stream endpoints are added separately.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping("/mine")
    public ResponseEntity<List<NotificationResponse>> getMyNotifications(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(notificationService.getMyNotifications(user.userId()));
    }

    @GetMapping("/mine/unread")
    public ResponseEntity<List<NotificationResponse>> getMyUnreadNotifications(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(notificationService.getMyUnreadNotifications(user.userId()));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markAsRead(@AuthenticationPrincipal AuthenticatedUser user,
                                                           @PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markAsRead(id, user.userId()));
    }

    @PatchMapping("/mine/read-all")
    public ResponseEntity<MessageResponse> markAllAsRead(@AuthenticationPrincipal AuthenticatedUser user) {
        int count = notificationService.markAllAsRead(user.userId());
        return ResponseEntity.ok(new MessageResponse(count + " notifications marked as read"));
    }
}
