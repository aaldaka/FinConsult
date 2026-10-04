package com.example.finConsult.controller;

import com.example.finConsult.model.request.ReasonRequest;
import com.example.finConsult.model.response.MessageResponse;
import com.example.finConsult.service.AdvisorService;
import com.example.finConsult.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {
    private final UserService userService;
    private final AdvisorService advisorService;

    @PatchMapping("/users/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')") //restricted/protected api
    public ResponseEntity<?> deactivateUser(@PathVariable Long id) {
        userService.deactivateUser(id);
        return ResponseEntity.ok(new MessageResponse("User deactivated"));
    }

    @PatchMapping("/advisors/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deactivateAdvisor(@PathVariable Long id, @RequestBody ReasonRequest request) {
        advisorService.deactivateAdvisor(id, request.getReason());
        return ResponseEntity.ok(new MessageResponse("Advisor deactivated"));
    }

}
