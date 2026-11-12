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
public class RestUserClient implements UserClient {

    private final RestClient restClient;

    public RestUserClient(@Qualifier("userServiceRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Map<Long, String> names(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        var ids = new ArrayList<>(new LinkedHashSet<>(userIds)); // no repeated ids in the request
        return UserServiceCalls.call(() -> restClient.post()
                .uri("/internal/users/names")
                .contentType(MediaType.APPLICATION_JSON)
                .body(ids)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<Long, String>>() {}), "User not found");
    }
}
