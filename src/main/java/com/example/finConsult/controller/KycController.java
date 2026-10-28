package com.example.finConsult.controller;

import com.example.finConsult.model.KycDocument;
import com.example.finConsult.model.User;
import com.example.finConsult.model.enums.DocumentType;
import com.example.finConsult.model.enums.KycStatus;
import com.example.finConsult.model.request.ReasonRequest;
import com.example.finConsult.model.response.ErrorResponse;
import com.example.finConsult.model.response.KycDocResponse;
import com.example.finConsult.security.MyUserDetails;
import com.example.finConsult.service.KycService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * KYC document submission for clients and admin review (approve/reject/download).
 */
@Tag(name = "KYC", description = "Identity document upload and review")
@RestController
@RequestMapping("/kyc")
@RequiredArgsConstructor
public class KycController {
    private final KycService kycService;

    @Operation(summary = "Client uploads a KYC document")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Document submitted and pending review", content = @Content(schema = @Schema(implementation = KycDocResponse.class))),
            @ApiResponse(responseCode = "400", description = "Missing/oversized/unsupported file, or a pending document of this type already exists", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not a client", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "A pending document of this type was created at the same moment", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<KycDocResponse> submit(
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "Type of document being submitted") @RequestParam DocumentType documentType,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = ((MyUserDetails) userDetails).getUser();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(kycService.submit(user, file, documentType));
    }

    @Operation(summary = "List my KYC documents")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of the caller's KYC documents", content = @Content(schema = @Schema(implementation = KycDocResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not a client", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/me")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<List<KycDocResponse>> getMyDocuments(@AuthenticationPrincipal UserDetails userDetails) {
        User user = ((MyUserDetails) userDetails).getUser();
        return ResponseEntity.ok(kycService.getMyDocuments(user));
    }

    @Operation(summary = "Admin lists KYC documents by status and document type")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of KYC documents", content = @Content(schema = @Schema(implementation = KycDocResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<KycDocResponse>> getByStatus(@Parameter(description = "Review status to filter by") @RequestParam(defaultValue = "PENDING") KycStatus status,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size,
                                                            @Parameter(description = "Document type to filter by; omit to return every type") DocumentType documentType) {
        Pageable p = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by("createdAt").descending());
        return ResponseEntity.ok(kycService.getByStatus(status, p, documentType));
    }

    @Operation(summary = "Admin approves a KYC document")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document approved", content = @Content(schema = @Schema(implementation = KycDocResponse.class))),
            @ApiResponse(responseCode = "400", description = "Document is not pending review", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Document not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Changed by someone else at the same moment, refresh and try again", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<KycDocResponse> approve(@Parameter(description = "ID of the document to approve") @PathVariable Long id,
                                                   @AuthenticationPrincipal UserDetails userDetails) {
        User admin = ((MyUserDetails) userDetails).getUser();
        return ResponseEntity.ok(kycService.approve(id, admin));
    }

    @Operation(summary = "Admin rejects a KYC document with a reason")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Document rejected", content = @Content(schema = @Schema(implementation = KycDocResponse.class))),
            @ApiResponse(responseCode = "400", description = "A rejection reason is required, or the document is not pending review", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Document not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Changed by someone else at the same moment, refresh and try again", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<KycDocResponse> reject(@Parameter(description = "ID of the document to reject") @PathVariable Long id,
                                                  @RequestBody ReasonRequest request, @AuthenticationPrincipal UserDetails userDetails) {
        User admin = ((MyUserDetails) userDetails).getUser();
        return ResponseEntity.ok(kycService.reject(id, request.getReason(), admin));
    }

    @Operation(summary = "Admin downloads a KYC document file")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "File stream of the stored document"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Document or file not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}/download")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Resource> download(@Parameter(description = "ID of the document to download") @PathVariable Long id) {
        KycDocument doc = kycService.getDocument(id);
        Resource resource = kycService.loadFile(doc);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + doc.getOriginalFileName() + "\"")
                .body(resource);
    }

    @Operation(summary = "Client uploads several KYC documents at once")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "All documents submitted and pending review", content = @Content(schema = @Schema(implementation = KycDocResponse.class))),
            @ApiResponse(responseCode = "400", description = "Files and types don't match up, a type is repeated, a file is invalid, or a pending document of that type exists", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not a client", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "A pending document of this type was created at the same moment", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/batch")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<List<KycDocResponse>> submitAll(@RequestPart("files") List<MultipartFile> files,
                                                          @Parameter(description = "One document type per file, in the same order") @RequestParam List<DocumentType> documentTypes,
                                                          @AuthenticationPrincipal UserDetails userDetails) {
        User user = ((MyUserDetails) userDetails).getUser();
        return ResponseEntity.status(HttpStatus.CREATED).body(kycService.submitAll(user, files, documentTypes));
    }

}
