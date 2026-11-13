package com.example.bookingservice.model.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// optional body of the internal cancel endpoints. A missing or blank reason falls back to the default text
@Getter
@Setter
public class InternalCancelRequest {
    @Size(max = 255) // the cancellation reason column is 255 long
    private String reason;
    private Long adminId;
}
