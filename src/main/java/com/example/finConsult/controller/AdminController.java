package com.example.finConsult.controller;

import com.example.finConsult.mapper.ResponseMapper;
import com.example.finConsult.model.Advisor;
import com.example.finConsult.model.User;
import com.example.finConsult.model.enums.AuditAction;
import com.example.finConsult.model.request.AdvisorRequest;
import com.example.finConsult.model.request.ReasonRequest;
import com.example.finConsult.model.response.AdvisorResponse;
import com.example.finConsult.model.response.AuditLogResponse;
import com.example.finConsult.model.response.ErrorResponse;
import com.example.finConsult.model.response.MessageResponse;
import com.example.finConsult.model.response.UserSummaryResponse;
import com.example.finConsult.security.MyUserDetails;
import com.example.finConsult.service.AdvisorService;
import com.example.finConsult.service.AuditLogService;
import com.example.finConsult.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin-only operations: creating advisor accounts, deactivating users/advisors,
 * and browsing the audit log.
 */
@Tag(name = "Admin", description = "Admin-only actions: create and deactivate accounts, audit logs")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {
    private final UserService userService;
    private final AdvisorService advisorService;
    private final AuditLogService auditLogService;
    private final ResponseMapper mapper;

    @Operation(summary = "Create an advisor account")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Advisor created", content = @Content(schema = @Schema(implementation = AdvisorResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email already in use", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/advisors")
    @PreAuthorize("hasRole('ADMIN')")
    // CHANGED: typed response body
    public ResponseEntity<AdvisorResponse> createAdvisor(@Valid @RequestBody AdvisorRequest request,
                                           @AuthenticationPrincipal UserDetails userDetails) {
        User admin = ((MyUserDetails) userDetails).getUser();
        Advisor advisor = advisorService.createAdvisor(request, admin);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toAdvisor(advisor));
    }

    @Operation(summary = "List clients that are not deactivated")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of active clients", content = @Content(schema = @Schema(implementation = UserSummaryResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/clients")
    @PreAuthorize("hasRole('ADMIN')")
    // CHANGED: typed response body
    public ResponseEntity<List<UserSummaryResponse>> getActiveClients() {
        return ResponseEntity.ok(userService.getActiveClients());
    }

    @Operation(summary = "Deactivate a user account and cancel their appointments")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User deactivated", content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "400", description = "Advisors must be deactivated through the advisor endpoint", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/users/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')") //restricted/protected api
    // CHANGED: typed response body
    public ResponseEntity<MessageResponse> deactivateUser(@Parameter(description = "ID of the user to deactivate") @PathVariable Long id,
                                             @AuthenticationPrincipal UserDetails userDetails) {
        User admin = ((MyUserDetails) userDetails).getUser();
        userService.deactivateUser(id, admin);
        return ResponseEntity.ok(new MessageResponse("User deactivated"));
    }

    @Operation(summary = "Deactivate an advisor (reason required) and cancel their appointments")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Advisor deactivated", content = @Content(schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "400", description = "A reason is required", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Advisor not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/advisors/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    // CHANGED: typed response body
    public ResponseEntity<MessageResponse> deactivateAdvisor(@Parameter(description = "ID of the advisor to deactivate") @PathVariable Long id,
                                                @Valid @RequestBody ReasonRequest request, @AuthenticationPrincipal UserDetails userDetails) {
        User admin = ((MyUserDetails) userDetails).getUser();
        advisorService.deactivateAdvisor(id, request.getReason(), admin);
        return ResponseEntity.ok(new MessageResponse("Advisor deactivated"));
    }

    @Operation(summary = "List audit logs, optionally filtered by action")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of audit logs", content = @Content(schema = @Schema(implementation = AuditLogResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogs(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                                                                @Parameter(description = "Audit action to filter by; omit to return all actions") @RequestParam(required = false) AuditAction action){
        org.springframework.data.domain.Pageable p = PageRequest.of(page, size, Sort.by("timestamp").descending());
        return ResponseEntity.ok(auditLogService.getLogs(p, action));
    }
}
