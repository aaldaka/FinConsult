package com.example.finConsult.controller;

import com.example.finConsult.model.User;
import com.example.finConsult.repository.NotificationRepository;
import com.example.finConsult.security.MyUserDetails;
import com.example.finConsult.service.NotificationService;
import com.example.finConsult.service.NotificationStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RequiredArgsConstructor
@RestController
@RequestMapping("/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    private final NotificationStreamService streamService;

    @GetMapping("/mine")
    public ResponseEntity<?> getMyNotifications(@AuthenticationPrincipal UserDetails userDetails) {
        User user = ((MyUserDetails) userDetails).getUser();
        return ResponseEntity.ok(notificationService.getMyNotifications(user));
    }

    @GetMapping("/mine/unread")
    public ResponseEntity<?> getMyUnreadNotifications(@AuthenticationPrincipal UserDetails userDetails) {
        User user = ((MyUserDetails) userDetails).getUser();
        return ResponseEntity.ok(notificationService.getMyUnreadNotifications(user));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        User user = ((MyUserDetails) userDetails).getUser();
        return ResponseEntity.ok(notificationService.markAsRead(id, user));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal UserDetails userDetails) {
        User user = ((MyUserDetails) userDetails).getUser();
        return streamService.subscribe(user.getId());
    }

}
