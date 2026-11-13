package com.example.bookingservice.controller;

import com.example.bookingservice.model.request.InternalCancelRequest;
import com.example.bookingservice.model.response.CancellationSummary;
import com.example.bookingservice.service.InternalCancellationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// called by the user service only, guarded by the internal key (see InternalKeyFilter). The body is optional
@RestController
@RequestMapping("/internal/appointments")
@RequiredArgsConstructor
public class InternalAppointmentController {

    private final InternalCancellationService cancellationService;

    @PostMapping("/cancel-for-advisor/{advisorId}")
    public ResponseEntity<CancellationSummary> cancelForAdvisor(@PathVariable Long advisorId,
                                                                @Valid @RequestBody(required = false) InternalCancelRequest request) {
        return ResponseEntity.ok(cancellationService.cancelForAdvisor(advisorId, reasonOf(request), adminOf(request)));
    }

    @PostMapping("/cancel-for-client/{userId}")
    public ResponseEntity<CancellationSummary> cancelForClient(@PathVariable Long userId,
                                                               @Valid @RequestBody(required = false) InternalCancelRequest request) {
        return ResponseEntity.ok(cancellationService.cancelForClient(userId, reasonOf(request), adminOf(request)));
    }

    private String reasonOf(InternalCancelRequest request) {
        return request == null ? null : request.getReason();
    }

    private Long adminOf(InternalCancelRequest request) {
        return request == null ? null : request.getAdminId();
    }
}
