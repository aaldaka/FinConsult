package com.example.documentservice.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

// looks up a file hash on VirusTotal. The free plan allows 4 requests a minute, so calls wait for a permit and results
// are cached. Without an API key the scan is skipped
@Slf4j
@Service
public class VirusTotalService {

    static final String DEFAULT_BASE_URL = "https://www.virustotal.com/api/v3";
    private static final int MAX_PERMITS = 4;
    private static final Duration MAX_WAIT = Duration.ofSeconds(65); // 5s more than the window, for a safe margin
    private static final Duration RELEASE_AFTER = Duration.ofSeconds(60);

    // 4 permits per minute (allowed free usage), FIFO, the doc pool has up to 8 threads so this is necessary
    private final Semaphore permits = new Semaphore(MAX_PERMITS, true);
    private final ScheduledExecutorService releaser = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "vt-permit-release");
        thread.setDaemon(true); //bg thread
        return thread;
    });
    private final Map<String, ScanResult> cache = new ConcurrentHashMap<>(); // same file twice = one call
    private final RestClient restClient;
    private final String apiKey;
    private final Duration maxWait;
    private final Duration releaseAfter;

    @Autowired
    public VirusTotalService(@Value("${VIRUSTOTAL_API_KEY:}") String apiKey,
                             @Value("${virustotal.base-url:" + DEFAULT_BASE_URL + "}") String baseUrl) {
        this(apiKey, baseUrl, MAX_WAIT, RELEASE_AFTER);
    }

    // the tests use short limiter times and a local fake server
    VirusTotalService(String apiKey, String baseUrl, Duration maxWait, Duration releaseAfter) {
        this.apiKey = apiKey;
        this.maxWait = maxWait;
        this.releaseAfter = releaseAfter;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory(); // send http req, opposite of controller
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    // the scan result, or SKIPPED if there is no key or no permit came back in time
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
        releaser.schedule(() -> permits.release(), releaseAfter.toMillis(), TimeUnit.MILLISECONDS); // released after the window regardless
        return fetchResult(sha256);
    }

    private boolean acquirePermit() {
        try {
            return permits.tryAcquire(maxWait.toMillis(), TimeUnit.MILLISECONDS);
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
            log.warn("VirusTotal lookup failed for {}: {}", shortHash(sha256), e.getMessage());
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
