package com.example.bookingservice.controller;

import com.example.bookingservice.mapper.AppointmentAssembler;
import com.example.bookingservice.model.Appointment;
import com.example.bookingservice.model.enums.AppointmentStatus;
import com.example.bookingservice.model.request.BookAppointmentRequest;
import com.example.bookingservice.model.request.ReasonRequest;
import com.example.bookingservice.model.response.AppointmentResponse;
import com.example.bookingservice.model.response.MessageResponse;
import com.example.bookingservice.security.AuthenticatedUser;
import com.example.bookingservice.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final AppointmentAssembler assembler;

    @Operation(summary = "Book an available slot", description = "The appointment stays pending until the advisor confirms it")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Appointment booked"),
            @ApiResponse(responseCode = "400", description = "The slot or service cannot be booked, or KYC is missing"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token"),
            @ApiResponse(responseCode = "403", description = "Caller is not a client, or the account is not active"),
            @ApiResponse(responseCode = "404", description = "Slot or service not found"),
            @ApiResponse(responseCode = "409", description = "Someone else booked the slot first")
    })
    @PostMapping("/book/{slotId}")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<AppointmentResponse> bookSlot(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long slotId,
                                                        @Valid @RequestBody BookAppointmentRequest request) {
        Appointment appointment = appointmentService.bookSlot(user.userId(), slotId, request.getServiceTypeId());
        return ResponseEntity.status(HttpStatus.CREATED).body(assembler.one(appointment));
    }

    @Operation(summary = "Confirm a pending appointment")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Appointment confirmed"),
            @ApiResponse(responseCode = "400", description = "Not your appointment, or it is not pending"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an advisor"),
            @ApiResponse(responseCode = "404", description = "Appointment not found"),
            @ApiResponse(responseCode = "409", description = "It was cancelled at the same time")
    })
    @PatchMapping("/{id}/confirm")
    @PreAuthorize("hasRole('ADVISOR')")
    public ResponseEntity<AppointmentResponse> confirmAppointment(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        Appointment appointment = appointmentService.confirmAppointment(user.userId(), id);
        return ResponseEntity.ok(assembler.one(appointment));
    }

    @Operation(summary = "Cancel an appointment",
            description = "The client or the advisor can cancel. A client cancelling less than 24 hours before adds a strike")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Appointment cancelled"),
            @ApiResponse(responseCode = "400", description = "Not your appointment, or it can no longer be cancelled"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token"),
            @ApiResponse(responseCode = "404", description = "Appointment not found"),
            @ApiResponse(responseCode = "409", description = "It was changed at the same time")
    })
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<MessageResponse> cancelAppointment(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                                                             @Valid @RequestBody ReasonRequest request) {
        appointmentService.cancelAppointment(id, user.userId(), request.getReason());
        return ResponseEntity.ok(new MessageResponse("Appointment cancelled"));
    }

    @GetMapping("/mine") // createdAt, newest first, when no sort is given
    public ResponseEntity<List<AppointmentResponse>> getMyAppointments(@AuthenticationPrincipal AuthenticatedUser user,
                                                                       @RequestParam(required = false) AppointmentStatus status,
                                                                       @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC) Sort sortBy) {
        List<Appointment> appointments = "ADVISOR".equals(user.role())
                ? appointmentService.getAdvisorAppointments(user.userId(), status, sortBy)
                : appointmentService.getClientAppointments(user.userId(), status, sortBy);
        return ResponseEntity.ok(assembler.many(appointments));
    }

    @GetMapping("/no-shows")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AppointmentResponse>> getNoShows() {
        return ResponseEntity.ok(assembler.many(appointmentService.getNoShows()));
    }

    @PatchMapping("/{id}/no-show")
    @PreAuthorize("hasRole('ADVISOR')")
    public ResponseEntity<AppointmentResponse> markNoShow(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return ResponseEntity.ok(assembler.one(appointmentService.markNoShow(user.userId(), id)));
    }

    @PatchMapping("/{id}/excuse")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AppointmentResponse> excuseNoShow(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                                                            @Valid @RequestBody ReasonRequest request) {
        return ResponseEntity.ok(assembler.one(appointmentService.excuseNoShow(user.userId(), id, request.getReason())));
    }
}
