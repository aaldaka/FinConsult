package com.example.finConsult.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
public class BookAppointmentRequest {
    @Schema(description = "ID of the service type being booked", example = "1")
    @NotNull(message = "serviceTypeId is required")
    private Long serviceTypeId;
}
