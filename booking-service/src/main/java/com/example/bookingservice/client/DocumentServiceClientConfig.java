package com.example.bookingservice.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

// the document service is called the same way as the user service: the internal key header and short timeouts
@Configuration
public class DocumentServiceClientConfig {

    @Bean("documentServiceRestClient")
    public RestClient documentServiceRestClient(@Value("${document-service.url:http://localhost:8084}") String baseUrl,
                                                @Value("${internal.api-key}") String internalKey) {
        return UserServiceClientConfig.restClientBuilder(baseUrl, internalKey).build();
    }
}
