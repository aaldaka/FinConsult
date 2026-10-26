package com.example.finConsult.controller;

import com.example.finConsult.model.ApplicationCertificate;
import com.example.finConsult.model.User;
import com.example.finConsult.model.enums.KycStatus;
import com.example.finConsult.model.request.AdvisorApplicationRequest;
import com.example.finConsult.model.request.ReasonRequest;
import com.example.finConsult.model.response.AdvisorApplicationResponse;
import com.example.finConsult.model.response.MyApplicationsResponse;
import com.example.finConsult.security.MyUserDetails;
import com.example.finConsult.service.AdvisorApplicationService;
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

@RestController
@RequestMapping("/advisor-applications")
@RequiredArgsConstructor
public class AdvisorApplicationController {

    private final AdvisorApplicationService applicationService;

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

    @GetMapping("/mine")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<MyApplicationsResponse> getMyApplications(@AuthenticationPrincipal UserDetails userDetails) {
        User applicant = ((MyUserDetails) userDetails).getUser();
        return ResponseEntity.ok(applicationService.getMyApplications(applicant));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AdvisorApplicationResponse>> getByStatus(
            @RequestParam(defaultValue = "PENDING") KycStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable p = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by("createdAt").descending());
        return ResponseEntity.ok(applicationService.getByStatus(status, p));
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdvisorApplicationResponse> approve(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User admin = ((MyUserDetails) userDetails).getUser();
        return ResponseEntity.ok(applicationService.approve(id, admin));
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdvisorApplicationResponse> reject(@PathVariable Long id, @Valid @RequestBody ReasonRequest request, @AuthenticationPrincipal UserDetails userDetails) {        User admin = ((MyUserDetails) userDetails).getUser();
        return ResponseEntity.ok(applicationService.reject(id, request.getReason(), admin));
    }

    @GetMapping("/certificates/{certificateId}/download")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Resource> downloadCertificate(
            @PathVariable Long certificateId) {
        ApplicationCertificate certificate = applicationService.getCertificate(certificateId);
        Resource resource = applicationService.loadCertificate(certificate);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(certificate.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + certificate.getOriginalFileName() + "\"")
                .body(resource);
    }
}
