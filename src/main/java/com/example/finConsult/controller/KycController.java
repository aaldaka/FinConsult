package com.example.finConsult.controller;

import com.example.finConsult.model.KycDocument;
import com.example.finConsult.model.User;
import com.example.finConsult.model.enums.DocumentType;
import com.example.finConsult.model.enums.KycStatus;
import com.example.finConsult.model.request.ReasonRequest;
import com.example.finConsult.model.response.KycDocResponse;
import com.example.finConsult.security.MyUserDetails;
import com.example.finConsult.service.KycService;
import org.springframework.core.io.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
import org.springframework.data.domain.Pageable;
import java.util.List;

@RestController
@RequestMapping("/kyc")
@RequiredArgsConstructor
public class KycController {
    private final KycService kycService;

    @PostMapping
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<KycDocResponse> submit(
            @RequestPart("file") MultipartFile file,
            @RequestParam DocumentType documentType,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = ((MyUserDetails) userDetails).getUser();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(kycService.submit(user, file, documentType));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<List<KycDocResponse>> getMyDocuments(@AuthenticationPrincipal UserDetails userDetails) {
        User user = ((MyUserDetails) userDetails).getUser();
        return ResponseEntity.ok(kycService.getMyDocuments(user));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<KycDocResponse>> getByStatus(@RequestParam(defaultValue = "PENDING") KycStatus status, @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size, DocumentType documentType) {
        Pageable p = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by("createdAt").descending());
        return ResponseEntity.ok(kycService.getByStatus(status, p, documentType));
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<KycDocResponse> approve(@PathVariable Long id, @AuthenticationPrincipal UserDetails userDetails) {
        User admin = ((MyUserDetails) userDetails).getUser();
        return ResponseEntity.ok(kycService.approve(id, admin));
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<KycDocResponse> reject(@PathVariable Long id, @RequestBody ReasonRequest request, @AuthenticationPrincipal UserDetails userDetails) {
        User admin = ((MyUserDetails) userDetails).getUser();
        return ResponseEntity.ok(kycService.reject(id, request.getReason(), admin));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        KycDocument doc = kycService.getDocument(id);
        Resource resource = kycService.loadFile(doc);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + doc.getOriginalFileName() + "\"")
                .body(resource);
    }
}