package com.example.documentservice.controller;

import com.example.documentservice.model.KycDocument;
import com.example.documentservice.model.enums.DocumentType;
import com.example.documentservice.model.enums.KycStatus;
import com.example.documentservice.model.request.ReasonRequest;
import com.example.documentservice.model.response.KycDocResponse;
import com.example.documentservice.security.AuthenticatedUser;
import com.example.documentservice.service.KycService;
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
@RequestMapping("/kyc")
@RequiredArgsConstructor
public class KycController {

    private final KycService kycService;

    @PostMapping
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<KycDocResponse> submit(@RequestPart("file") MultipartFile file, @RequestParam DocumentType documentType,
                                                 @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(kycService.submit(user.userId(), file, documentType));
    }

    @PostMapping("/batch")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<List<KycDocResponse>> submitAll(@RequestPart("files") List<MultipartFile> files,
                                                          @RequestParam List<DocumentType> documentTypes,
                                                          @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(kycService.submitAll(user.userId(), files, documentTypes));
    }

    @PutMapping(value = "/{id}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<KycDocResponse> replaceFile(@PathVariable Long id, @RequestPart("file") MultipartFile file,
                                                      @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(kycService.replaceFile(user.userId(), id, file));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<List<KycDocResponse>> getMyDocuments(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(kycService.getMyDocuments(user.userId()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<KycDocResponse>> getByStatus(@RequestParam(defaultValue = "PENDING") KycStatus status,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size,
                                                            DocumentType documentType) {
        Pageable p = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by("createdAt").descending());
        return ResponseEntity.ok(kycService.getByStatus(status, p, documentType));
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<KycDocResponse> approve(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser admin) {
        return ResponseEntity.ok(kycService.approve(id, admin.userId()));
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<KycDocResponse> reject(@PathVariable Long id, @Valid @RequestBody ReasonRequest request,
                                                 @AuthenticationPrincipal AuthenticatedUser admin) {
        return ResponseEntity.ok(kycService.reject(id, request.getReason(), admin.userId()));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        KycDocument doc = kycService.getDocument(id);
        Resource resource = kycService.loadFile(doc);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getOriginalFileName() + "\"")
                .body(resource);
    }
}
