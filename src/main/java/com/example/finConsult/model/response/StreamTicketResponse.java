package com.example.finConsult.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StreamTicketResponse {
    @Schema(description = "Single-use ticket for GET /notifications/stream?ticket=..., valid for 30 seconds")
    private String ticket;
}
