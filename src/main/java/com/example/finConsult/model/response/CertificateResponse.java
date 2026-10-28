package com.example.finConsult.model.response;

import com.example.finConsult.model.enums.CertificateStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CertificateResponse {
    @Schema(example = "1")
    private Long id;
    @Schema(example = "cfa_level1.pdf")
    private String originalFileName;
    @Schema(example = "184320")
    private long fileSize;
    @Schema(description = "PROCESSING while the background check runs, then VALID or FLAGGED")
    private CertificateStatus status;
    @Schema(description = "File type read from the file bytes, not the extension", example = "PDF")
    private String detectedType;
}
