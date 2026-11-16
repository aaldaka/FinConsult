package com.example.documentservice.controller;

import com.example.documentservice.model.ApplicationCertificate;
import com.example.documentservice.model.enums.KycStatus;
import com.example.documentservice.model.request.AdvisorApplicationRequest;
import com.example.documentservice.model.request.ReasonRequest;
import com.example.documentservice.model.response.AdvisorApplicationResponse;
import com.example.documentservice.model.response.MyApplicationsResponse;
import com.example.documentservice.security.AuthenticatedUser;
import com.example.documentservice.service.AdvisorApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/advisor-applications")
@RequiredArgsConstructor
public class AdvisorApplicationController {

    private final AdvisorApplicationService applicationService;

    @Operation(summary = "Apply to become an advisor", description = "Send the application and at least one certificate (PDF, JPG or PNG)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Application submitted"),
            @ApiResponse(responseCode = "400", description = "Validation error, a missing certificate, or the client is not eligible"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token"),
            @ApiResponse(responseCode = "403", description = "Caller is not a client"),
            @ApiResponse(responseCode = "503", description = "The user service is not available")
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<AdvisorApplicationResponse> submit(@Valid @RequestPart("application") AdvisorApplicationRequest request,
                                                             @RequestPart("certificates") List<MultipartFile> certificates,
                                                             @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(applicationService.submit(user.userId(), request, certificates));
    }

    @Operation(summary = "List my advisor applications and how many attempts are left")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "My applications"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token"),
            @ApiResponse(responseCode = "403", description = "Caller is not a client")
    })
    @GetMapping("/mine")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<MyApplicationsResponse> getMyApplications(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(applicationService.getMyApplications(user.userId()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AdvisorApplicationResponse>> getByStatus(@RequestParam(defaultValue = "PENDING") KycStatus status,
                                                                        @RequestParam(defaultValue = "0") int page,
                                                                        @RequestParam(defaultValue = "20") int size) {
        Pageable p = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by("createdAt").descending());
        return ResponseEntity.ok(applicationService.getByStatus(status, p));
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdvisorApplicationResponse> approve(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser admin) {
        return ResponseEntity.ok(applicationService.approve(id, admin.userId()));
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdvisorApplicationResponse> reject(@PathVariable Long id, @Valid @RequestBody ReasonRequest request,
                                                             @AuthenticationPrincipal AuthenticatedUser admin) {
        return ResponseEntity.ok(applicationService.reject(id, request.getReason(), admin.userId()));
    }

    @PutMapping(value = "/certificates/{certificateId}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<AdvisorApplicationResponse> replaceCertificate(@PathVariable Long certificateId,
                                                                         @RequestPart("file") MultipartFile file,
                                                                         @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(applicationService.replaceCertificate(user.userId(), certificateId, file));
    }

    @GetMapping("/certificates/{certificateId}/download")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Resource> downloadCertificate(@PathVariable Long certificateId) {
        ApplicationCertificate certificate = applicationService.getCertificate(certificateId);
        Resource resource = applicationService.loadCertificate(certificate);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(certificate.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + certificate.getOriginalFileName() + "\"")
                .body(resource);
    }
}
