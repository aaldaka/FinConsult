package com.example.bookingservice.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RestStrikeClient implements StrikeClient {

    private final RestClient restClient;

    public RestStrikeClient(@Qualifier("userServiceRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public StrikeResult addStrike(Long userId) {
        return UserServiceCalls.call(() -> restClient.post()
                .uri("/internal/users/{id}/strikes", userId)
                .retrieve()
                .body(StrikeResult.class), "User not found");
    }

    @Override
    public StrikeResult removeStrike(Long userId) {
        return UserServiceCalls.call(() -> restClient.delete()
                .uri("/internal/users/{id}/strikes", userId)
                .retrieve()
                .body(StrikeResult.class), "User not found");
    }
}
