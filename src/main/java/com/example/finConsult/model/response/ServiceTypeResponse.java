package com.example.finConsult.model.response;

import com.example.finConsult.model.enums.DocumentType;
import com.example.finConsult.model.enums.Specialty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ServiceTypeResponse {
    @Schema(example = "1")
    private Long id;
    @Schema(example = "Retirement Planning")
    private String name;
    private String description;
    @Schema(example = "30")
    private int durationMinutes;
    private boolean requiresKyc;
    @Schema(description = "Document type required when requiresKyc is true and a specific type is needed; null means any approved document", nullable = true)
    private DocumentType requiredDocumentType;
    @Schema(description = "Specialty an advisor must have to offer this service; null means any advisor can", nullable = true)
    private Specialty requiredSpecialty;
}
