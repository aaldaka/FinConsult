package com.example.bookingservice.model.response;

import com.example.bookingservice.model.enums.SlotStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SlotResponse {
    private Long id;
    private Long advisorId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private SlotStatus status;
}
