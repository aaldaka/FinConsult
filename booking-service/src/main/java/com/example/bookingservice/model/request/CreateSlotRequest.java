package com.example.bookingservice.model.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CreateSlotRequest {

    @NotNull(message = "startingTime is required")
    @Future(message = "startingTime must be in the future")
    private LocalDateTime startingTime;

    @NotNull(message = "finishingTime is required")
    private LocalDateTime finishingTime;
}
