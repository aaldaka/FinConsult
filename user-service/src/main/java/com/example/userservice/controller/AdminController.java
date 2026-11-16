package com.example.userservice.controller;

import com.example.userservice.mapper.ResponseMapper;
import com.example.userservice.model.Advisor;
import com.example.userservice.model.User;
import com.example.userservice.model.request.AdvisorRequest;
import com.example.userservice.model.request.ReasonRequest;
import com.example.userservice.model.response.AdvisorResponse;
import com.example.userservice.model.response.MessageResponse;
import com.example.userservice.model.response.UserSummaryResponse;
import com.example.userservice.security.MyUserDetails;
import com.example.userservice.service.AdvisorService;
import com.example.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin-only operations on accounts: creating advisor accounts and deactivating users and advisors.
 * (The audit log endpoint belongs to another area.)
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final AdvisorService advisorService;
    private final ResponseMapper mapper;

    @PostMapping("/advisors")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdvisorResponse> createAdvisor(@Valid @RequestBody AdvisorRequest request,
                                                         @AuthenticationPrincipal UserDetails userDetails) {
        User admin = ((MyUserDetails) userDetails).getUser();
        Advisor advisor = advisorService.createAdvisor(request, admin);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toAdvisor(advisor));
    }

    @Operation(summary = "List the active clients")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The clients"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin")
    })
    @GetMapping("/clients")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserSummaryResponse>> getActiveClients() {
        return ResponseEntity.ok(userService.getActiveClients());
    }

    @PatchMapping("/users/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')") //restricted/protected api
    public ResponseEntity<MessageResponse> deactivateUser(@PathVariable Long id,
                                                          @AuthenticationPrincipal UserDetails userDetails) {
        User admin = ((MyUserDetails) userDetails).getUser();
        userService.deactivateUser(id, admin);
        return ResponseEntity.ok(new MessageResponse("User deactivated"));
    }

    @PatchMapping("/advisors/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> deactivateAdvisor(@PathVariable Long id,
                                                             @Valid @RequestBody ReasonRequest request,
                                                             @AuthenticationPrincipal UserDetails userDetails) {
        User admin = ((MyUserDetails) userDetails).getUser();
        advisorService.deactivateAdvisor(id, request.getReason(), admin);
        return ResponseEntity.ok(new MessageResponse("Advisor deactivated"));
    }
}
