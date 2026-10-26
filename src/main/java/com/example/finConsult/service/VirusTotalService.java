package com.example.finConsult.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class VirusTotalService {
    // 5s more for a safe margin
    private static final int MAX_WAIT_SECONDS = 65;

    // 4 permits per minute (allowed free usage), FIFO, doc thread is 8 so this is necessary
    private final Semaphore permits = new Semaphore(4, true);

    private final ScheduledExecutorService releaser = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "vt-permit-release");
        thread.setDaemon(true); //bg thread
        return thread;
    });

    private final Map<String, ScanResult> cache = new ConcurrentHashMap<>(); // same file twice = one call
    private final RestClient restClient;
    private final String apiKey;

    public VirusTotalService(@Value("${VIRUSTOTAL_API_KEY:}") String apiKey) {
        this.apiKey = apiKey;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory(); // send http req, opposite of controller
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .baseUrl("https://www.virustotal.com/api/v3")
                .requestFactory(factory)
                .build();
    }


}