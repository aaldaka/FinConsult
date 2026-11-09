package com.example.notificationservice.controller;

import com.example.notificationservice.model.response.MessageResponse;
import com.example.notificationservice.model.response.NotificationResponse;
import com.example.notificationservice.model.response.StreamTicketResponse;
import com.example.notificationservice.security.AuthenticatedUser;
import com.example.notificationservice.service.NotificationService;
import com.example.notificationservice.service.NotificationStreamService;
import com.example.notificationservice.service.StreamTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * In-app notification listing and read-state endpoints, plus the SSE stream for real-time push.
 * The user id comes from the token, or from the ticket on the stream.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    private final NotificationStreamService streamService;
    private final StreamTicketService streamTicketService;

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

    // the browser's EventSource cannot send the token header, so the client gets a ticket here first
    @PostMapping("/stream-ticket")
    public ResponseEntity<StreamTicketResponse> createStreamTicket(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(new StreamTicketResponse(streamTicketService.issue(user.userId())));
    }

    // authenticated with ?ticket= from the endpoint above, see JwtRequestFilter
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal AuthenticatedUser user) {
        return streamService.subscribe(user.userId());
    }
}
