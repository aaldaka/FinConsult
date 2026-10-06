package com.example.finConsult.model.response;

import com.example.finConsult.model.enums.DocumentType;
import com.example.finConsult.model.enums.KycStatus;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class KycDocResponse {
    private Long id;
    private String userEmail;
    private DocumentType documentType;
    private String originalFileName;
    private long fileSize;
    private KycStatus status;
    private String rejectionReason;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
}
