package com.example.finConsult.controller;

import com.example.finConsult.model.User;
import com.example.finConsult.model.request.ReasonRequest;
import com.example.finConsult.model.response.MessageResponse;
import com.example.finConsult.security.MyUserDetails;
import com.example.finConsult.service.AdvisorService;
import com.example.finConsult.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {
    private final UserService userService;
    private final AdvisorService advisorService;

    @PatchMapping("/users/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')") //restricted/protected api
    public ResponseEntity<?> deactivateUser(@PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        User admin = ((MyUserDetails) userDetails).getUser();
        userService.deactivateUser(id, admin);
        return ResponseEntity.ok(new MessageResponse("User deactivated"));
    }

    @PatchMapping("/advisors/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deactivateAdvisor(@PathVariable Long id, @RequestBody ReasonRequest request, @AuthenticationPrincipal UserDetails userDetails) {
        User admin = ((MyUserDetails) userDetails).getUser();
        advisorService.deactivateAdvisor(id, request.getReason(), admin);
        return ResponseEntity.ok(new MessageResponse("Advisor deactivated"));
    }

}
