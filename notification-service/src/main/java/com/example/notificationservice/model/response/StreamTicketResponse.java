package com.example.notificationservice.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StreamTicketResponse {
    // single-use ticket for GET /notifications/stream?ticket=..., valid for 30 seconds
    private String ticket;
}
