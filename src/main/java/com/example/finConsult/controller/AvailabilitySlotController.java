package com.example.finConsult.controller;

import com.example.finConsult.exception.InformationNotFound;
import com.example.finConsult.mapper.ResponseMapper;
import com.example.finConsult.model.Advisor;
import com.example.finConsult.model.AvailabilitySlot;
import com.example.finConsult.model.User;
import com.example.finConsult.model.request.CreateSlotRequest;
import com.example.finConsult.model.response.MessageResponse;
import com.example.finConsult.repository.AdvisorRepository;
import com.example.finConsult.security.MyUserDetails;
import com.example.finConsult.service.AvailabilitySlotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/slots")
@RequiredArgsConstructor
public class AvailabilitySlotController { //posting the slot
    private final AdvisorRepository advisorRepository;
    private final AvailabilitySlotService availabilitySlotService;
    private final ResponseMapper mapper;

    @PostMapping
    @PreAuthorize("hasRole('ADVISOR') or hasRole('ADMIN')")
    public ResponseEntity<?> createSlot(@AuthenticationPrincipal UserDetails userDetails, @Valid @RequestBody CreateSlotRequest request) {
        User user = ((MyUserDetails) userDetails).getUser();
        Advisor advisor = advisorRepository.findByUser(user)
                .orElseThrow(() -> new InformationNotFound("Advisor profile not found"));

        AvailabilitySlot createdSlot = availabilitySlotService.createSlot(advisor.getId(), request);
        return ResponseEntity.ok(mapper.toSlot(createdSlot));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADVISOR')")
    public ResponseEntity<?> deleteSlot(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        User user = ((MyUserDetails) userDetails).getUser();
        Advisor advisor = advisorRepository.findByUser(user)
                .orElseThrow(() -> new InformationNotFound("Advisor profile not found"));

        availabilitySlotService.deleteSlot(advisor.getId(), id);
        return ResponseEntity.ok(new MessageResponse("Slot deleted successfully"));
    }

    @GetMapping("/advisor/{advisorId}")
    public ResponseEntity<?> getAvailableSlots(@PathVariable Long advisorId) {
        List<AvailabilitySlot> slots = availabilitySlotService.getAvailableSlots(advisorId);
        return ResponseEntity.ok(slots.stream().map(mapper::toSlot).toList());
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('ADVISOR')")
    public ResponseEntity<?> getMySlots(@AuthenticationPrincipal UserDetails userDetails) {
        User user = ((MyUserDetails) userDetails).getUser();
        Advisor advisor = advisorRepository.findByUser(user)
                .orElseThrow(() -> new InformationNotFound("Advisor profile not found"));

        List<AvailabilitySlot> slots = availabilitySlotService.getMySlots(advisor.getId());
        return ResponseEntity.ok(slots.stream().map(mapper::toSlot).toList());
    }
}
