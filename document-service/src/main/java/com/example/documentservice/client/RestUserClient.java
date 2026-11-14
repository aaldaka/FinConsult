package com.example.documentservice.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collection;
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
}
