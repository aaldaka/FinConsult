package com.example.bookingservice.model.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BookAppointmentRequest {
    @NotNull(message = "serviceTypeId is required")
    private Long serviceTypeId;
}
