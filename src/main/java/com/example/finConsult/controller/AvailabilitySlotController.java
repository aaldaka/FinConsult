package com.example.finConsult.controller;

import com.example.finConsult.exception.InformationNotFound;
import com.example.finConsult.mapper.ResponseMapper;
import com.example.finConsult.model.Advisor;
import com.example.finConsult.model.AvailabilitySlot;
import com.example.finConsult.model.User;
import com.example.finConsult.model.request.CreateSlotRequest;
import com.example.finConsult.model.response.ErrorResponse;
import com.example.finConsult.model.response.MessageResponse;
import com.example.finConsult.model.response.SlotResponse;
import com.example.finConsult.repository.AdvisorRepository;
import com.example.finConsult.security.MyUserDetails;
import com.example.finConsult.service.AvailabilitySlotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Advisor availability slot management: creation, deletion, and browsing.
 */
@Tag(name = "Availability Slots", description = "Advisor availability slots")
@RestController
@RequestMapping("/slots")
@RequiredArgsConstructor
public class AvailabilitySlotController { //posting the slot
    private final AdvisorRepository advisorRepository;
    private final AvailabilitySlotService availabilitySlotService;
    private final ResponseMapper mapper;

    @Operation(summary = "Advisor creates an availability slot")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Slot created", content = @Content(schema = @Schema(implementation = SlotResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid time range or overlaps an existing slot", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not an advisor or admin", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Advisor profile not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    @PreAuthorize("hasRole('ADVISOR') or hasRole('ADMIN')")
    public ResponseEntity<?> createSlot(@AuthenticationPrincipal UserDetails userDetails, @Valid @RequestBody CreateSlotRequest request) {
        User user = ((MyUserDetails) userDetails).getUser();
        Advisor advisor = advisorRepository.findByUser(user)
                .orElseThrow(() -> new InformationNotFound("Advisor profile not found"));

        AvailabilitySlot createdSlot = availabilitySlotService.createSlot(advisor.getId(), request);
        return ResponseEntity.ok(mapper.toSlot(createdSlot));
    }

    @Operation(summary = "Advisor deletes an unbooked slot")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Slot deleted", content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Not the caller's slot, or the slot is already booked", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not an advisor", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Slot or advisor profile not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADVISOR')")
    public ResponseEntity<?> deleteSlot(@AuthenticationPrincipal UserDetails userDetails,
                                         @Parameter(description = "ID of the slot to delete") @PathVariable Long id) {
        User user = ((MyUserDetails) userDetails).getUser();
        Advisor advisor = advisorRepository.findByUser(user)
                .orElseThrow(() -> new InformationNotFound("Advisor profile not found"));

        availabilitySlotService.deleteSlot(advisor.getId(), id);
        return ResponseEntity.ok(new MessageResponse("Slot deleted successfully"));
    }

    @Operation(summary = "List an advisor's available slots")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of available slots", content = @Content(schema = @Schema(implementation = SlotResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Advisor not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/advisor/{advisorId}")
    public ResponseEntity<?> getAvailableSlots(@Parameter(description = "ID of the advisor whose slots to list") @PathVariable Long advisorId) {
        List<AvailabilitySlot> slots = availabilitySlotService.getAvailableSlots(advisorId);
        return ResponseEntity.ok(slots.stream().map(mapper::toSlot).toList());
    }

    @Operation(summary = "List my slots")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of the caller's slots", content = @Content(schema = @Schema(implementation = SlotResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not an advisor", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Advisor profile not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
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
