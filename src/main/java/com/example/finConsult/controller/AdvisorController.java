package com.example.finConsult.controller;

import com.example.finConsult.mapper.ResponseMapper;
import com.example.finConsult.model.Advisor;
import com.example.finConsult.model.User;
import com.example.finConsult.model.enums.Specialty;;
import com.example.finConsult.model.request.UpdateAdvisorRequest;
import com.example.finConsult.security.MyUserDetails;
import com.example.finConsult.service.AdvisorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/advisors")
public class AdvisorController {
    private final AdvisorService advisorService;
    private final ResponseMapper mapper;

    @PreAuthorize("hasRole('ADVISOR')") // wire admin later
    @PatchMapping("/me")
    public ResponseEntity<?> updateAdvisor(@AuthenticationPrincipal UserDetails userDetails, @Valid @RequestBody UpdateAdvisorRequest updateAdvisorRequest) {
        User user = ((MyUserDetails) userDetails).getUser();
        return ResponseEntity.ok(mapper.toAdvisor(advisorService.updateAdvisor(user, updateAdvisorRequest)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getAdvisor(@PathVariable Long id) {
        return ResponseEntity.ok(mapper.toAdvisor(advisorService.getById(id)));
    }

    @GetMapping // can have specific advisors otherwise all
    public ResponseEntity<?> searchAdvisors(@RequestParam(required = false) Specialty specialty) {
        List<Advisor> advisors = (specialty != null)
                ? advisorService.findBySpecialty(specialty)
                : advisorService.findAllActive();
        return ResponseEntity.ok(advisors.stream().map(mapper::toAdvisor).toList());
    }

}
