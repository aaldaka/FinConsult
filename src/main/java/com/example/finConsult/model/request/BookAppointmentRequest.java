package com.example.finConsult.model.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
public class BookAppointmentRequest {
    @NotNull(message = "serviceTypeId is required")
    private Long serviceTypeId;
}
