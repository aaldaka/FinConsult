package com.example.finConsult.controller;

import com.example.finConsult.model.ApplicationCertificate;
import com.example.finConsult.model.User;
import com.example.finConsult.model.enums.KycStatus;
import com.example.finConsult.model.request.AdvisorApplicationRequest;
import com.example.finConsult.model.request.ReasonRequest;
import com.example.finConsult.model.response.AdvisorApplicationResponse;
import com.example.finConsult.model.response.ErrorResponse;
import com.example.finConsult.model.response.MyApplicationsResponse;
import com.example.finConsult.security.MyUserDetails;
import com.example.finConsult.service.AdvisorApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Clients apply to become advisors, admins approve or reject the applications.
 */
@Tag(name = "Advisor Applications", description = "Apply to become an advisor and review applications")
@RestController
@RequestMapping("/advisor-applications")
@RequiredArgsConstructor
public class AdvisorApplicationController {

    private final AdvisorApplicationService applicationService;

    @Operation(summary = "Client applies to become an advisor with certificates")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Application submitted, certificates are checked in the background", content = @Content(schema = @Schema(implementation = AdvisorApplicationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing fields, no certificates, or a certificate is not PDF/JPG/PNG", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not a client", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Not eligible: no approved ID, a pending application already exists, or all 3 attempts used", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<AdvisorApplicationResponse> submit(
            @Valid @RequestPart("application") AdvisorApplicationRequest request,
            @RequestPart("certificates") List<MultipartFile> certificates,
            @AuthenticationPrincipal UserDetails userDetails) {
        User applicant = ((MyUserDetails) userDetails).getUser();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(applicationService.submit(applicant, request, certificates));
    }

    @Operation(summary = "List my applications and how many attempts are left")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The caller's applications with attempts used and the max", content = @Content(schema = @Schema(implementation = MyApplicationsResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not a client", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/mine")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<MyApplicationsResponse> getMyApplications(@AuthenticationPrincipal UserDetails userDetails) {
        User applicant = ((MyUserDetails) userDetails).getUser();
        return ResponseEntity.ok(applicationService.getMyApplications(applicant));
    }

    @Operation(summary = "Admin lists applications by status")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of applications, newest first", content = @Content(schema = @Schema(implementation = AdvisorApplicationResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AdvisorApplicationResponse>> getByStatus(
            @Parameter(description = "Review status to filter by") @RequestParam(defaultValue = "PENDING") KycStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable p = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by("createdAt").descending());
        return ResponseEntity.ok(applicationService.getByStatus(status, p));
    }

    @Operation(summary = "Admin approves an application, the applicant becomes an advisor")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Application approved and advisor profile created", content = @Content(schema = @Schema(implementation = AdvisorApplicationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Application is not pending, or certificates are still being checked", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Application not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Applicant is no longer a client or is blacklisted, or another admin reviewed it first", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdvisorApplicationResponse> approve(
            @Parameter(description = "ID of the application to approve") @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User admin = ((MyUserDetails) userDetails).getUser();
        return ResponseEntity.ok(applicationService.approve(id, admin));
    }

    @Operation(summary = "Admin rejects an application with a reason")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Application rejected", content = @Content(schema = @Schema(implementation = AdvisorApplicationResponse.class))),
            @ApiResponse(responseCode = "400", description = "A rejection reason is required, or the application is not pending", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Application not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Another admin reviewed it first", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdvisorApplicationResponse> reject(
            @Parameter(description = "ID of the application to reject") @PathVariable Long id,
            @Valid @RequestBody ReasonRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User admin = ((MyUserDetails) userDetails).getUser();
        return ResponseEntity.ok(applicationService.reject(id, request.getReason(), admin));
    }

    @Operation(summary = "Admin downloads a certificate file")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "File stream of the stored certificate"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Certificate or file not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/certificates/{certificateId}/download")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Resource> downloadCertificate(
            @Parameter(description = "ID of the certificate to download") @PathVariable Long certificateId) {
        ApplicationCertificate certificate = applicationService.getCertificate(certificateId);
        Resource resource = applicationService.loadCertificate(certificate);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(certificate.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + certificate.getOriginalFileName() + "\"")
                .body(resource);
    }
}
