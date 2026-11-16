package com.example.notificationservice.controller;

import com.example.notificationservice.model.enums.AuditAction;
import com.example.notificationservice.model.response.AuditLogResponse;
import com.example.notificationservice.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// the audit log, admins only (see SecurityConfiguration). The same path, parameters and page shape as the main app
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminLogController {

    private final AuditLogService auditLogService;

    @Operation(summary = "List audit logs, newest first, optionally filtered by action")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of audit logs"),
            @ApiResponse(responseCode = "400", description = "Unknown action"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin"),
            @ApiResponse(responseCode = "503", description = "The user service is not available")
    })
    @GetMapping("/logs")
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogs(@RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size,
                                                               @RequestParam(required = false) AuditAction action) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        return ResponseEntity.ok(auditLogService.getLogs(pageable, action));
    }
}
