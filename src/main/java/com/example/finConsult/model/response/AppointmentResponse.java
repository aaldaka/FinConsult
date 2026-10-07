package com.example.finConsult.model.response;

import com.example.finConsult.model.enums.AppointmentStatus;
import com.example.finConsult.model.enums.Specialty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AppointmentResponse {
    @Schema(example = "1")
    private Long id;
    private AppointmentStatus status;
    @Schema(description = "Free-text notes on the appointment", nullable = true)
    private String notes;
    @Schema(description = "Reason given when the appointment is cancelled", nullable = true)
    private String cancellationReason;
    private boolean excused;
    @Schema(description = "Reason given when a no-show is excused", nullable = true)
    private String excuseReason;
    @Schema(example = "1")
    private Long advisorId;
    @Schema(example = "Jane Doe")
    private String advisorName;
    private Specialty advisorSpecialty;
    @Schema(example = "John Smith")
    private String clientName;
    @Schema(example = "Retirement Planning")
    private String serviceName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createdAt;
}
