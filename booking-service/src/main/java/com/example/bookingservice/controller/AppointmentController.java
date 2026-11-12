package com.example.bookingservice.controller;

import com.example.bookingservice.mapper.AppointmentAssembler;
import com.example.bookingservice.model.Appointment;
import com.example.bookingservice.model.request.BookAppointmentRequest;
import com.example.bookingservice.model.request.ReasonRequest;
import com.example.bookingservice.model.response.AppointmentResponse;
import com.example.bookingservice.model.response.MessageResponse;
import com.example.bookingservice.security.AuthenticatedUser;
import com.example.bookingservice.service.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final AppointmentAssembler assembler;

    @PostMapping("/book/{slotId}")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<AppointmentResponse> bookSlot(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long slotId,
                                                        @Valid @RequestBody BookAppointmentRequest request) {
        Appointment appointment = appointmentService.bookSlot(user.userId(), slotId, request.getServiceTypeId());
        return ResponseEntity.status(HttpStatus.CREATED).body(assembler.one(appointment));
    }

    @PatchMapping("/{id}/confirm")
    @PreAuthorize("hasRole('ADVISOR')")
    public ResponseEntity<AppointmentResponse> confirmAppointment(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        Appointment appointment = appointmentService.confirmAppointment(user.userId(), id);
        return ResponseEntity.ok(assembler.one(appointment));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<MessageResponse> cancelAppointment(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                                                             @Valid @RequestBody ReasonRequest request) {
        appointmentService.cancelAppointment(id, user.userId(), request.getReason());
        return ResponseEntity.ok(new MessageResponse("Appointment cancelled"));
    }
}
