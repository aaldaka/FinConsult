package com.example.userservice.controller;

import com.example.userservice.mapper.ResponseMapper;
import com.example.userservice.model.Advisor;
import com.example.userservice.model.User;
import com.example.userservice.model.enums.Specialty;
import com.example.userservice.model.request.UpdateAdvisorRequest;
import com.example.userservice.model.response.AdvisorResponse;
import com.example.userservice.security.MyUserDetails;
import com.example.userservice.service.AdvisorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Advisor browsing/lookup (any logged in user) plus self-service profile updates for advisors.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/advisors")
public class AdvisorController {

    private final AdvisorService advisorService;
    private final ResponseMapper mapper;

    @PreAuthorize("hasRole('ADVISOR')") // wire admin later
    @PatchMapping("/me")
    public ResponseEntity<AdvisorResponse> updateAdvisor(@AuthenticationPrincipal UserDetails userDetails, @Valid @RequestBody UpdateAdvisorRequest updateAdvisorRequest) {
        User user = ((MyUserDetails) userDetails).getUser();
        return ResponseEntity.ok(mapper.toAdvisor(advisorService.updateAdvisor(user, updateAdvisorRequest)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdvisorResponse> getAdvisor(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toAdvisor(advisorService.getById(id)));
    }

    @Operation(summary = "List active advisors, optionally by specialty")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The advisors"),
            @ApiResponse(responseCode = "400", description = "Unknown specialty"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token")
    })
    @GetMapping // can have specific advisors otherwise all
    public ResponseEntity<List<AdvisorResponse>> searchAdvisors(@RequestParam(required = false) Specialty specialty) {
        List<Advisor> advisors = (specialty != null)
                ? advisorService.findBySpecialty(specialty)
                : advisorService.findAllActive();
        return ResponseEntity.ok(advisors.stream().map(mapper::toAdvisor).toList());
    }
}
