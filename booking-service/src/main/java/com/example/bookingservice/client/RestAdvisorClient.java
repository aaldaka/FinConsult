package com.example.bookingservice.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;

@Component
public class RestAdvisorClient implements AdvisorClient {

    private final RestClient restClient;

    public RestAdvisorClient(@Qualifier("userServiceRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public AdvisorInfo getById(Long advisorId) {
        return UserServiceCalls.call(() -> restClient.get()
                .uri("/internal/advisors/{id}", advisorId)
                .retrieve()
                .body(AdvisorInfo.class), "Advisor not found");
    }

    @Override
    public AdvisorInfo getByUserId(Long userId) {
        return UserServiceCalls.call(() -> restClient.get()
                .uri("/internal/advisors/by-user/{userId}", userId)
                .retrieve()
                .body(AdvisorInfo.class), "Advisor profile not found");
    }

    @Override
    public Map<Long, AdvisorInfo> lookup(Collection<Long> advisorIds) {
        if (advisorIds == null || advisorIds.isEmpty()) {
            return Map.of();
        }
        var ids = new ArrayList<>(new LinkedHashSet<>(advisorIds)); // no repeated ids in the request
        return UserServiceCalls.call(() -> restClient.post()
                .uri("/internal/advisors/lookup")
                .contentType(MediaType.APPLICATION_JSON)
                .body(ids)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<Long, AdvisorInfo>>() {}), "Advisor not found");
    }
}
