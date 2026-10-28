package com.example.finConsult.controller;

import com.example.finConsult.model.User;
import com.example.finConsult.model.response.ErrorResponse;
import com.example.finConsult.model.response.MessageResponse;
import com.example.finConsult.model.response.NotificationResponse;
import com.example.finConsult.security.MyUserDetails;
import com.example.finConsult.service.NotificationService;
import com.example.finConsult.service.NotificationStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * In-app notification listing/read-state endpoints, plus the SSE stream for
 * real-time push.
 */
@Tag(name = "Notifications", description = "In-app and real-time notifications")
@RequiredArgsConstructor
@RestController
@RequestMapping("/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    private final NotificationStreamService streamService;


    @Operation(summary = "List my notifications")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of the caller's notifications", content = @Content(schema = @Schema(implementation = NotificationResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/mine")
    public ResponseEntity<?> getMyNotifications(@AuthenticationPrincipal UserDetails userDetails) {
        User user = ((MyUserDetails) userDetails).getUser();
        return ResponseEntity.ok(notificationService.getMyNotifications(user));
    }

    @Operation(summary = "List my unread notifications")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of the caller's unread notifications", content = @Content(schema = @Schema(implementation = NotificationResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/mine/unread")
    public ResponseEntity<?> getMyUnreadNotifications(@AuthenticationPrincipal UserDetails userDetails) {
        User user = ((MyUserDetails) userDetails).getUser();
        return ResponseEntity.ok(notificationService.getMyUnreadNotifications(user));
    }

    @Operation(summary = "Mark a notification as read")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification marked as read", content = @Content(schema = @Schema(implementation = NotificationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Notification belongs to another user", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Notification not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(@AuthenticationPrincipal UserDetails userDetails,
                                         @Parameter(description = "ID of the notification to mark as read") @PathVariable Long id) {
        User user = ((MyUserDetails) userDetails).getUser();
        return ResponseEntity.ok(notificationService.markAsRead(id, user));
    }

    @Operation(summary = "Mark all my notifications as read")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Unread notifications marked as read", content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/mine/read-all")
    public ResponseEntity<?> markAllAsRead(@AuthenticationPrincipal UserDetails userDetails) {
        User user = ((MyUserDetails) userDetails).getUser();
        int count = notificationService.markAllAsRead(user);
        return ResponseEntity.ok(new MessageResponse(count + " notifications marked as read"));
    }

    @Operation(summary = "Subscribe to real-time notifications (Server-Sent Events)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "SSE stream opened"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal UserDetails userDetails) {
        User user = ((MyUserDetails) userDetails).getUser();
        return streamService.subscribe(user.getId());
    }

}
