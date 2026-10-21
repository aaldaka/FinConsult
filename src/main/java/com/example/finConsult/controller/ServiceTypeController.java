package com.example.finConsult.controller;

import com.example.finConsult.model.response.ErrorResponse;
import com.example.finConsult.model.response.ServiceTypeResponse;
import com.example.finConsult.service.ServiceTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only catalog of bookable service types, used by clients to pick a service
 * when booking an appointment.
 */
@Tag(name = "Service Types", description = "Catalog of bookable services")
@RestController
@RequestMapping("/service-types")
@RequiredArgsConstructor
public class ServiceTypeController {

    private final ServiceTypeService serviceTypeService;

    @Operation(summary = "List active service types")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of active service types", content = @Content(schema = @Schema(implementation = ServiceTypeResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<?> listActive() {
        return ResponseEntity.ok(serviceTypeService.getActiveServices());
    }
}
