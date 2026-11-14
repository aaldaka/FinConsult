package com.example.documentservice.client;

import com.example.documentservice.exception.NotEligible;
import com.example.documentservice.model.enums.Specialty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Component
public class RestUserClient implements UserClient {

    private final RestClient restClient;

    public RestUserClient(@Qualifier("userServiceRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public UserInfo getById(Long userId) {
        return UserServiceCalls.call(() -> restClient.get()
                .uri("/internal/users/{id}", userId)
                .retrieve()
                .body(UserInfo.class), "User not found");
    }

    @Override
    public Map<Long, UserSummary> lookup(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        var ids = new ArrayList<>(new LinkedHashSet<>(userIds)); // no repeated ids in the request
        return UserServiceCalls.call(() -> restClient.post()
                .uri("/internal/users/lookup")
                .contentType(MediaType.APPLICATION_JSON)
                .body(ids)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<Long, UserSummary>>() {}), "User not found");
    }

    @Override
    public List<AdminInfo> admins() {
        return UserServiceCalls.call(() -> restClient.get()
                .uri("/internal/users/admins")
                .retrieve()
                .body(new ParameterizedTypeReference<List<AdminInfo>>() {}), "User not found");
    }

    // a 409 means the user service refused the promotion (already an advisor, or not a client): its message is kept
    @Override
    public PromotionResult promote(Long userId, String title, Specialty specialty, int yearsOfExperience) {
        Map<String, Object> body = new HashMap<>();
        body.put("userId", userId);
        body.put("title", title);
        body.put("specialty", specialty);
        body.put("yearsOfExperience", yearsOfExperience);
        return UserServiceCalls.call(() -> {
            try {
                return restClient.post()
                        .uri("/internal/advisors/promote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(PromotionResult.class);
            } catch (HttpClientErrorException.Conflict e) {
                throw new NotEligible(messageOf(e, "This user cannot be promoted to advisor"));
            }
        }, "User not found");
    }

    @Override
    public AdvisorProfile getAdvisorByUser(Long userId) {
        return UserServiceCalls.call(() -> restClient.get()
                .uri("/internal/advisors/by-user/{userId}", userId)
                .retrieve()
                .body(AdvisorProfile.class), "Advisor not found");
    }

    private static String messageOf(HttpClientErrorException e, String fallback) {
        try {
            Map<?, ?> error = e.getResponseBodyAs(Map.class);
            Object message = error == null ? null : error.get("message");
            return message instanceof String text && !text.isBlank() ? text : fallback;
        } catch (RuntimeException unreadable) {
            return fallback;
        }
    }
}
