package com.example.bookingservice.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class UserServiceClientConfig {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    static RestClient.Builder restClientBuilder(String baseUrl, String internalKey, Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(UserServiceCalls.KEY_HEADER, internalKey)
                .requestFactory(requestFactory);
    }

    static RestClient.Builder restClientBuilder(String baseUrl, String internalKey) {
        return restClientBuilder(baseUrl, internalKey, CONNECT_TIMEOUT, READ_TIMEOUT);
    }

    @Bean("userServiceRestClient")
    public RestClient userServiceRestClient(@Value("${user-service.url}") String baseUrl,
                                            @Value("${internal.api-key}") String internalKey) {
        return restClientBuilder(baseUrl, internalKey).build();
    }
}
