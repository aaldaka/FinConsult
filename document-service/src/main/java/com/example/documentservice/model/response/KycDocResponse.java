package com.example.documentservice.model.response;

import com.example.documentservice.model.enums.DocumentType;
import com.example.documentservice.model.enums.KycStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    private String detectedType;
    private boolean flagged;
    private String flagReason; // only in the admin view
}
