package com.example.finConsult.controller;

import com.example.finConsult.mapper.ResponseMapper;
import com.example.finConsult.model.Advisor;
import com.example.finConsult.model.User;
import com.example.finConsult.model.enums.Specialty;;
import com.example.finConsult.model.request.UpdateAdvisorRequest;
import com.example.finConsult.model.response.AdvisorResponse;
import com.example.finConsult.model.response.ErrorResponse;
import com.example.finConsult.security.MyUserDetails;
import com.example.finConsult.service.AdvisorService;
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
 * Public advisor browsing/lookup plus self-service profile updates for advisors.
 */
@Tag(name = "Advisors", description = "Browse advisors and manage the advisor profile")
@RestController
@RequiredArgsConstructor
@RequestMapping("/advisors")
public class AdvisorController {
    private final AdvisorService advisorService;
    private final ResponseMapper mapper;

    @Operation(summary = "Advisor updates their own details")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Advisor profile updated", content = @Content(schema = @Schema(implementation = AdvisorResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not an advisor", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Advisor profile not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('ADVISOR')") // wire admin later
    @PatchMapping("/me")
    public ResponseEntity<AdvisorResponse> updateAdvisor(@AuthenticationPrincipal UserDetails userDetails, @Valid @RequestBody UpdateAdvisorRequest updateAdvisorRequest) {
        User user = ((MyUserDetails) userDetails).getUser();
        return ResponseEntity.ok(mapper.toAdvisor(advisorService.updateAdvisor(user, updateAdvisorRequest)));
    }

    @Operation(summary = "Get an advisor by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Advisor found", content = @Content(schema = @Schema(implementation = AdvisorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Advisor not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<AdvisorResponse> getAdvisor(@Parameter(description = "ID of the advisor to fetch") @PathVariable Long id) {
        return ResponseEntity.ok(mapper.toAdvisor(advisorService.getById(id)));
    }

    @Operation(summary = "List active advisors, optionally filtered by specialty")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of active advisors", content = @Content(schema = @Schema(implementation = AdvisorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping // can have specific advisors otherwise all
    public ResponseEntity<List<AdvisorResponse>> searchAdvisors(@Parameter(description = "Specialty to filter by; omit to return advisors of every specialty") @RequestParam(required = false) Specialty specialty) {
        List<Advisor> advisors = (specialty != null)
                ? advisorService.findBySpecialty(specialty)
                : advisorService.findAllActive();
        return ResponseEntity.ok(advisors.stream().map(mapper::toAdvisor).toList());
    }

}
