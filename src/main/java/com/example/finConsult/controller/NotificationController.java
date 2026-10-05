package com.example.finConsult.controller;

import com.example.finConsult.model.User;
import com.example.finConsult.repository.NotificationRepository;
import com.example.finConsult.security.MyUserDetails;
import com.example.finConsult.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/notifications")
public class NotificationController {
    private final NotificationService notificationService;

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

}
