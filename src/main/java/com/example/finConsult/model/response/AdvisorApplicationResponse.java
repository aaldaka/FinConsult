package com.example.finConsult.model.response;

import com.example.finConsult.model.enums.KycStatus;
import com.example.finConsult.model.enums.Specialty;
import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(example = "1")
    private Long id;
    @Schema(example = "hessa.albinali@example.com")
    private String applicantEmail;
    @Schema(example = "Hessa Al-Binali")
    private String applicantName;
    @Schema(example = "Retirement Planner")
    private String title;
    private Specialty specialty;
    @Schema(example = "4")
    private int yearsOfExperience;
    private KycStatus status;
    @Schema(description = "Only set when the application was rejected")
    private String rejectionReason;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private List<CertificateResponse> certificates;
}
