package com.example.finConsult.model.response;

import com.example.finConsult.model.enums.SlotStatus;
import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(example = "1")
    private Long id;
    @Schema(example = "1")
    private Long advisorId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private SlotStatus status;
}
