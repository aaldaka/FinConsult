package com.example.finConsult.model.response;

import com.example.finConsult.model.enums.AppointmentStatus;
import com.example.finConsult.model.enums.Specialty;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AppointmentResponse {
    private Long id;
    private AppointmentStatus status;
    private String notes;
    private String cancellationReason;
    private boolean excused;
    private String excuseReason;
    private Long advisorId;
    private String advisorName;
    private Specialty advisorSpecialty;
    private String clientName;
    private String serviceName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createdAt;
}