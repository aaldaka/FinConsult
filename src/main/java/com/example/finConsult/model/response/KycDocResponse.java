package com.example.finConsult.model.response;

import com.example.finConsult.model.enums.DocumentType;
import com.example.finConsult.model.enums.KycStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class KycDocResponse {
    @Schema(example = "1")
    private Long id;
    @Schema(example = "client@example.com")
    private String userEmail;
    private DocumentType documentType;
    @Schema(example = "passport.pdf")
    private String originalFileName;
    @Schema(example = "204800")
    private long fileSize;
    private KycStatus status;
    @Schema(description = "Reason given when the document is rejected", nullable = true)
    private String rejectionReason;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
}
