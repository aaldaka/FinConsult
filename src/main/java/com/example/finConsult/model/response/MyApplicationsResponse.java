package com.example.finConsult.model.response;

import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class MyApplicationsResponse {
    private List<AdvisorApplicationResponse> applications;
    private long attemptsUsed;
    private int maxAttempts;
}
