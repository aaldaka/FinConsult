package com.example.finConsult.model.response;

import com.example.finConsult.model.enums.SlotStatus;
import lombok.*;
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