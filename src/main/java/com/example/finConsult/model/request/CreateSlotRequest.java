package com.example.finConsult.model.request;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
public class CreateSlotRequest {
    private LocalDateTime startingTime;
    private LocalDateTime finishingTime;
}