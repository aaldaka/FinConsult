package com.example.bookingservice.controller;

import com.example.bookingservice.client.AdvisorClient;
import com.example.bookingservice.mapper.ResponseMapper;
import com.example.bookingservice.model.AvailabilitySlot;
import com.example.bookingservice.model.request.CreateSlotRequest;
import com.example.bookingservice.model.response.MessageResponse;
import com.example.bookingservice.model.response.SlotResponse;
import com.example.bookingservice.security.AuthenticatedUser;
import com.example.bookingservice.service.AvailabilitySlotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/slots")
@RequiredArgsConstructor
public class AvailabilitySlotController { //posting the slot

    private final AdvisorClient advisorClient;
    private final AvailabilitySlotService availabilitySlotService;
    private final ResponseMapper mapper;

    // 404 if the caller has no advisor row, 503 if the user service does not answer
    private Long advisorIdOf(AuthenticatedUser user) {
        return advisorClient.getByUserId(user.userId()).id();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADVISOR')") // admins have no advisor profile, so this was only ever a 404 for them
    public ResponseEntity<SlotResponse> createSlot(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody CreateSlotRequest request) {
        AvailabilitySlot createdSlot = availabilitySlotService.createSlot(advisorIdOf(user), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toSlot(createdSlot));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADVISOR')")
    public ResponseEntity<MessageResponse> deleteSlot(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        availabilitySlotService.deleteSlot(advisorIdOf(user), id);
        return ResponseEntity.ok(new MessageResponse("Slot deleted successfully"));
    }

    @GetMapping("/advisor/{advisorId}")
    public ResponseEntity<List<SlotResponse>> getAvailableSlots(@PathVariable Long advisorId) {
        List<AvailabilitySlot> slots = availabilitySlotService.getAvailableSlots(advisorId);
        return ResponseEntity.ok(slots.stream().map(mapper::toSlot).toList());
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('ADVISOR')")
    public ResponseEntity<List<SlotResponse>> getMySlots(@AuthenticationPrincipal AuthenticatedUser user) {
        List<AvailabilitySlot> slots = availabilitySlotService.getMySlots(advisorIdOf(user));
        return ResponseEntity.ok(slots.stream().map(mapper::toSlot).toList());
    }
}
