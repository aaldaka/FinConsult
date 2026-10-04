package com.example.finConsult.controller;

import com.example.finConsult.exception.InformationNotFound;
import com.example.finConsult.model.Advisor;
import com.example.finConsult.model.AvailabilitySlot;
import com.example.finConsult.model.User;
import com.example.finConsult.model.request.CreateSlotRequest;
import com.example.finConsult.model.request.UpdateAdvisorRequest;
import com.example.finConsult.repository.AdvisorRepository;
import com.example.finConsult.security.MyUserDetails;
import com.example.finConsult.service.AdvisorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/advisors")
public class AdvisorController {
    private final AdvisorService advisorService;

    @PreAuthorize("hasRole('ADVISOR')") // wire admin later
    @PatchMapping("/me")
    public ResponseEntity<?> updateAdvisor(@AuthenticationPrincipal UserDetails userDetails, @RequestBody UpdateAdvisorRequest updateAdvisorRequest) {
        User user = ((MyUserDetails) userDetails).getUser();
        return ResponseEntity.ok(advisorService.updateAdvisor(user, updateAdvisorRequest));
    }

}
