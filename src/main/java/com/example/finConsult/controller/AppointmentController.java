package com.example.finConsult.controller;

import com.example.finConsult.mapper.ResponseMapper;
import com.example.finConsult.model.Advisor;
import com.example.finConsult.model.Appointment;
import com.example.finConsult.model.User;
import com.example.finConsult.model.enums.AppointmentStatus;
import com.example.finConsult.model.enums.UserRole;
import com.example.finConsult.model.request.BookAppointmentRequest;
import com.example.finConsult.model.request.ReasonRequest;
import com.example.finConsult.model.response.AppointmentResponse;
import com.example.finConsult.model.response.ErrorResponse;
import com.example.finConsult.model.response.MessageResponse;
import com.example.finConsult.security.MyUserDetails;
import com.example.finConsult.service.AdvisorService;
import com.example.finConsult.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Appointment lifecycle endpoints: booking, advisor confirmation, cancellation,
 * listing, no-show marking and no-show excusal.
 */
@Tag(name = "Appointments", description = "Book, confirm, cancel and list appointments")
@RestController
@RequestMapping("/appointments")
@RequiredArgsConstructor
public class AppointmentController { // patch to partially update things instead of rewriting = more effort/code

    private final AppointmentService appointmentService;
    private final AdvisorService advisorService;
    private final ResponseMapper mapper;

    @Operation(summary = "Book an available slot for a service type")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Appointment booked, pending advisor confirmation", content = @Content(schema = @Schema(implementation = AppointmentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Advisor inactive, specialty mismatch or missing KYC", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not a client", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Slot or service type not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Slot was booked by someone else first (only one booking wins)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))

    })
    @PostMapping("/book/{slotId}")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<?> bookSlot(@AuthenticationPrincipal UserDetails userDetails,
                                       @Parameter(description = "ID of the availability slot to book") @PathVariable Long slotId,
                                       @Valid @RequestBody BookAppointmentRequest bookAppointmentRequest) {
         User client = ((MyUserDetails) userDetails).getUser();
         Appointment appointment = appointmentService.bookSlot(client, slotId, bookAppointmentRequest.getServiceTypeId());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toAppointment(appointment));
    }

    @Operation(summary = "Advisor confirms a pending appointment")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Appointment confirmed", content = @Content(schema = @Schema(implementation = AppointmentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Not the advisor's appointment, or appointment is not pending", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not an advisor", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Appointment or advisor profile not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Changed by someone else at the same moment, refresh and try again", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/confirm")
    @PreAuthorize("hasRole('ADVISOR')")
    public ResponseEntity<?> confirmAppointment(@AuthenticationPrincipal UserDetails userDetails,
                                                 @Parameter(description = "ID of the appointment to confirm") @PathVariable Long id) {
         User user = ((MyUserDetails) userDetails).getUser();
         Advisor advisor = advisorService.getByUser(user);
         Appointment appointment = appointmentService.confirmAppointment(advisor.getId(), id);
        return ResponseEntity.ok(mapper.toAppointment(appointment));
    }

    @Operation(summary = "Cancel an appointment (late cancellations add a strike)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Appointment cancelled", content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Caller is not a party to the appointment, or it is already cancelled/no-show", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Appointment not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Changed by someone else at the same moment, refresh and try again", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<?> cancelAppointment(@AuthenticationPrincipal UserDetails userDetails,
                                                @Parameter(description = "ID of the appointment to cancel") @PathVariable Long id,
                                                @Valid @RequestBody ReasonRequest request) {
        User user = ((MyUserDetails) userDetails).getUser();
        appointmentService.cancelAppointment(id, user, request.getReason());
        return ResponseEntity.ok(new MessageResponse("Appointment cancelled"));
    }

    @Operation(summary = "List my appointments, filter with ?status= and sort with ?sort=field,direction")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of appointments", content = @Content(schema = @Schema(implementation = AppointmentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Unsupported sort field", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/mine") //default to createdAt in desc order if no sortby was present
    public ResponseEntity<?> getMyAppointments(@AuthenticationPrincipal UserDetails userDetails,
                                               @Parameter(description = "Appointment status to filter by; omit to return every status") @RequestParam(required = false) AppointmentStatus status,
                                               @ParameterObject @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC) Sort sortBy) {
        User user = ((MyUserDetails) userDetails).getUser();

        if (user.getRole() == UserRole.ADVISOR) {
            Advisor advisor = advisorService.getByUser(user);
            return ResponseEntity.ok(appointmentService.getAdvisorAppointments(advisor.getId(), status, sortBy)
                    .stream().map(mapper::toAppointment).toList());
        } else {
            return ResponseEntity.ok(appointmentService.getClientAppointments(user, status, sortBy)
                    .stream().map(mapper::toAppointment).toList());
        }
    }

    @Operation(summary = "Advisor marks a client as a no-show")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Appointment marked as no-show", content = @Content(schema = @Schema(implementation = AppointmentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Not the advisor's appointment, not confirmed, or grace period has not elapsed", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not an advisor", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Appointment or advisor profile not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Changed by someone else at the same moment, refresh and try again", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/no-show")
    @PreAuthorize("hasRole('ADVISOR')")
    public ResponseEntity<?> markNoShow(@AuthenticationPrincipal UserDetails userDetails,
                                         @Parameter(description = "ID of the appointment to mark") @PathVariable Long id) {
        User user = ((MyUserDetails) userDetails).getUser();
        Advisor advisor = advisorService.getByUser(user);
        return ResponseEntity.ok(mapper.toAppointment(appointmentService.markNoShow(advisor.getId(), id)));
    }

    @Operation(summary = "Admin excuses a no-show and recalculates strikes")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "No-show excused", content = @Content(schema = @Schema(implementation = AppointmentResponse.class))),
            @ApiResponse(responseCode = "400", description = "A reason is required, or the appointment was not marked as a no-show", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Appointment not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Changed by someone else at the same moment, refresh and try again", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/excuse")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> excuseNoShow(@AuthenticationPrincipal UserDetails userDetails,
                                           @Parameter(description = "ID of the no-show appointment to excuse") @PathVariable Long id,
                                           @Valid @RequestBody ReasonRequest request) {
        User admin = ((MyUserDetails) userDetails).getUser();
        return ResponseEntity.ok(mapper.toAppointment(appointmentService.excuseNoShow(id, request.getReason(), admin)));
    }
}
