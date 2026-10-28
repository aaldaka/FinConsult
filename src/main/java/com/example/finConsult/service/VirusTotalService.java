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
import java.util.concurrent.*;

/**
 * Looks up a file hash on VirusTotal. The free plan allows 4 requests a minute, so calls
 * wait for a permit and results are cached. Without an API key the scan is skipped.
 */
@Slf4j
@Service
public class VirusTotalService {
    // 5s more for a safe margin
    private static final int MAX_WAIT_SECONDS = 65;
    private static final int MAX_PERMITS = 4;

    // 4 permits per minute (allowed free usage), FIFO, doc thread is 8 so this is necessary
    private final Semaphore permits = new Semaphore(MAX_PERMITS, true);

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
    /** @return the scan result, or SKIPPED if there is no key or no permit came back in time */
    public ScanResult lookup(String sha256) {
        if (apiKey == null || apiKey.isBlank()) {
            return ScanResult.skipped("no API key");
        }
        // check cache first
        ScanResult cached = cache.get(sha256);
        if (cached != null) {
            return cached;
        }

        if (!acquirePermit()) {
            return ScanResult.skipped("rate limit");
        }

        releaser.schedule(() -> permits.release(), 60, TimeUnit.SECONDS); // after a min release it regardless

        return fetchResult(sha256);
    }

    private boolean acquirePermit() {
        try {
            return permits.tryAcquire(MAX_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private ScanResult fetchResult(String sha256) {
        try {
            Map<?, ?> body = restClient.get()
                    .uri("/files/{hash}", sha256)
                    .header("x-apikey", apiKey)
                    .retrieve()
                    .body(Map.class);

            int malicious = readMalicious(body);
            ScanResult result = malicious > 0 ? ScanResult.malicious(malicious) : ScanResult.clean();
            cache.put(sha256, result);

            return result;

        } catch (HttpClientErrorException.NotFound e) {
            ScanResult result = ScanResult.unknown();
            cache.put(sha256, result);
            return result;


        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("VirusTotal quota exceeded");
            return ScanResult.skipped("quota exceeded");

        } catch (RestClientException | ClassCastException | NullPointerException e) {
            log.warn("VirusTotal lookup failed for {}: {}",
                    shortHash(sha256), e.getMessage());
            return ScanResult.skipped("API error");
        }
    }

    private int readMalicious(Map<?, ?> body) {
        Map<?, ?> data = (Map<?, ?>) body.get("data");
        Map<?, ?> attributes = (Map<?, ?>) data.get("attributes");
        Map<?, ?> stats = (Map<?, ?>) attributes.get("last_analysis_stats");

        Object malicious = stats.get("malicious");
        return malicious instanceof Number ? ((Number) malicious).intValue() : 0;
    }

    private String shortHash(String sha256) {
        return sha256.substring(0, Math.min(12, sha256.length()));
    }

    @PreDestroy
    void shutdown() {
        releaser.shutdownNow();
    }
}