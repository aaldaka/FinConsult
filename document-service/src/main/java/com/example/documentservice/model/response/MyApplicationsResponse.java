package com.example.documentservice.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
