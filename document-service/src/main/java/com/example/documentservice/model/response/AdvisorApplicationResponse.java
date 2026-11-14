package com.example.documentservice.model.response;

import com.example.documentservice.model.enums.KycStatus;
import com.example.documentservice.model.enums.Specialty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AdvisorApplicationResponse {
    private Long id;
    private String applicantEmail;
    private String applicantName;
    private String title;
    private Specialty specialty;
    private int yearsOfExperience;
    private KycStatus status;
    private String rejectionReason;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private List<CertificateResponse> certificates;
}
