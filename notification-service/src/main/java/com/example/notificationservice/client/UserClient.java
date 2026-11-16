package com.example.notificationservice.client;

import com.example.notificationservice.exception.ServiceUnavailable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;

// the email and name of users, from the user service's /internal/users/lookup with the internal key, in one call for
// a whole page. Any failure (an error status, a timeout, a refused connection) becomes a ServiceUnavailable, a 503,
// the same as document-service's admin lists
@Slf4j
@Component
public class UserClient {

    static final String KEY_HEADER = "X-Internal-Key";
    private static final String DOWN = "The user service is not available, please try again later";

    private final RestClient restClient;

    public UserClient(@Value("${user-service.url}") String baseUrl, @Value("${internal.api-key}") String internalKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(KEY_HEADER, internalKey)
                .requestFactory(requestFactory)
                .build();
    }

    public Map<Long, UserSummary> lookup(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, UserSummary> users;
        try {
            users = restClient.post()
                    .uri("/internal/users/lookup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ArrayList<>(new LinkedHashSet<>(userIds)))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<Long, UserSummary>>() {});
        } catch (RestClientException e) {
            log.warn("The user service call failed: {}", e.getMessage());
            throw new ServiceUnavailable(DOWN);
        }
        if (users == null) {
            log.warn("The user service answered with an empty body");
            throw new ServiceUnavailable(DOWN);
        }
        return users;
    }
}
