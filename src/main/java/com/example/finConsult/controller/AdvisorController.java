package com.example.finConsult.controller;

import com.example.finConsult.model.User;
import com.example.finConsult.model.enums.Specialty;;
import com.example.finConsult.model.request.UpdateAdvisorRequest;
import com.example.finConsult.security.MyUserDetails;
import com.example.finConsult.service.AdvisorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/{id}")
    public ResponseEntity<?> getAdvisor(@PathVariable Long id) {
        return ResponseEntity.ok(advisorService.getById(id));
    }

    @GetMapping // can have specific advisors otherwise all
    public ResponseEntity<?> searchAdvisors(@RequestParam(required = false) Specialty specialty) {
        if (specialty != null) {
            return ResponseEntity.ok(advisorService.findBySpecialty(specialty));
        }
        return ResponseEntity.badRequest().body("Please provide a specialty to search by");
    }

}
