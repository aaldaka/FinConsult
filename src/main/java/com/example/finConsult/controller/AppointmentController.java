package com.example.finConsult.controller;

import com.example.finConsult.exception.InformationNotFound;
import com.example.finConsult.model.Advisor;
import com.example.finConsult.model.Appointment;
import com.example.finConsult.model.User;
import com.example.finConsult.model.enums.AppointmentStatus;
import com.example.finConsult.model.enums.UserRole;
import com.example.finConsult.model.request.BookAppointmentRequest;
import com.example.finConsult.model.request.ReasonRequest;
import com.example.finConsult.model.response.MessageResponse;
import com.example.finConsult.repository.AdvisorRepository;
import com.example.finConsult.security.MyUserDetails;
import com.example.finConsult.service.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/appointments")
@RequiredArgsConstructor
public class AppointmentController { // patch to partially update things instead of rewriting = more effort/code

    private final AppointmentService appointmentService;
    private final AdvisorRepository advisorRepository;

    @PostMapping("/book/{slotId}")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<?> bookSlot(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long slotId, @Valid @RequestBody BookAppointmentRequest bookAppointmentRequest) {
         User client = ((MyUserDetails) userDetails).getUser();
         Appointment appointment = appointmentService.bookSlot(client, slotId, bookAppointmentRequest.getServiceTypeId());
         return ResponseEntity.ok(appointment);
    }

    @PatchMapping("/{id}/confirm")
    @PreAuthorize("hasRole('ADVISOR')")
    public ResponseEntity<?> confirmAppointment(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
         User user = ((MyUserDetails) userDetails).getUser();
         Advisor advisor = advisorRepository.findByUser(user).orElseThrow(() -> new InformationNotFound("Advisor profile not found"));
         Appointment appointment = appointmentService.confirmAppointment(advisor.getId(), id);
         return ResponseEntity.ok(appointment);
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<?> cancelAppointment(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
        User user = ((MyUserDetails) userDetails).getUser();
        appointmentService.cancelAppointment(id, user, request.getReason());
        return ResponseEntity.ok(new MessageResponse("Appointment cancelled"));
    }

    @GetMapping("/mine") //default to createdAt in desc order if no sortby was present
    public ResponseEntity<?> getMyAppointments(@AuthenticationPrincipal UserDetails userDetails, @RequestParam(required = false) AppointmentStatus status,
                                               @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC) Sort sortBy) {
        User user = ((MyUserDetails) userDetails).getUser();

        if (user.getRole() == UserRole.ADVISOR) {
            Advisor advisor = advisorRepository.findByUser(user)
                    .orElseThrow(() -> new InformationNotFound("Advisor profile not found"));
            return ResponseEntity.ok(appointmentService.getAdvisorAppointments(advisor.getId(), status, sortBy));
        } else {
            return ResponseEntity.ok(appointmentService.getClientAppointments(user, status, sortBy));
        }
    }

    @PatchMapping("/{id}/no-show")
    @PreAuthorize("hasRole('ADVISOR')")
    public ResponseEntity<?> markNoShow(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        User user = ((MyUserDetails) userDetails).getUser();
        Advisor advisor = advisorRepository.findByUser(user)
                .orElseThrow(() -> new InformationNotFound("Advisor profile not found"));
        return ResponseEntity.ok(appointmentService.markNoShow(advisor.getId(), id));
    }

    @PatchMapping("/{id}/excuse")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> excuseNoShow(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
        User admin = ((MyUserDetails) userDetails).getUser();
        return ResponseEntity.ok(appointmentService.excuseNoShow(id, request.getReason(), admin));
    }
}