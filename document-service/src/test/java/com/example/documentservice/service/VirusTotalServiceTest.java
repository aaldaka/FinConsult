package com.example.documentservice.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// no Spring and never the real VirusTotal: a tiny local server plays it, answering by the hash in the path
// (/files/<hash>), and the limiter runs with times of a fraction of a second
class VirusTotalServiceTest {

    private static final String KEY = "test-key";

    private HttpServer fake;
    private String baseUrl;
    private final AtomicInteger calls = new AtomicInteger();
    private final List<String> apiKeysSeen = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startTheFakeServer() throws Exception {
        fake = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        fake.createContext("/files/", exchange -> {
            calls.incrementAndGet();
            apiKeysSeen.add(exchange.getRequestHeaders().getFirst("x-apikey"));
            String hash = exchange.getRequestURI().getPath().substring("/files/".length());
            int status;
            String body;
            switch (hash.startsWith("clean") ? "clean" : hash) { // clean, clean-1, clean-2 ... are all clean files
                case "malicious" -> { status = 200; body = stats(3); }
                case "clean" -> { status = 200; body = stats(0); }
                case "unknown" -> { status = 404; body = "{}"; }
                case "busy" -> { status = 429; body = "{}"; }
                case "garbled" -> { status = 200; body = "{\"data\": {}}"; }
                default -> { status = 500; body = "{}"; }
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        fake.start();
        baseUrl = "http://127.0.0.1:" + fake.getAddress().getPort();
    }

    @AfterEach
    void stopTheFakeServer() {
        fake.stop(0);
    }

    private static String stats(int malicious) {
        return "{\"data\": {\"attributes\": {\"last_analysis_stats\": {\"malicious\": " + malicious + ", \"harmless\": 60}}}}";
    }

    private VirusTotalService service() {
        return new VirusTotalService(KEY, baseUrl, Duration.ofSeconds(5), Duration.ofSeconds(60));
    }

    @Test
    void eachAnswerBecomesTheRightResult() {
        VirusTotalService service = service();

        assertEquals(ScanResult.Status.MALICIOUS, service.lookup("malicious").status());
        assertEquals(3, service.lookup("malicious").maliciousCount());
        assertEquals(ScanResult.Status.CLEAN, service.lookup("clean").status());
        assertEquals(ScanResult.Status.UNKNOWN, service.lookup("unknown").status());
        assertTrue(apiKeysSeen.stream().allMatch(KEY::equals), "the key goes in the x-apikey header");
    }

    @Test
    void aQuotaErrorAServerErrorOrAnUnreadableBodyIsSkipped() {
        VirusTotalService service = new VirusTotalService(KEY, baseUrl, Duration.ofSeconds(5), Duration.ofMillis(10));

        assertEquals(new ScanResult(ScanResult.Status.SKIPPED, 0, "quota exceeded"), service.lookup("busy"));
        assertEquals(new ScanResult(ScanResult.Status.SKIPPED, 0, "API error"), service.lookup("broken"));
        assertEquals(new ScanResult(ScanResult.Status.SKIPPED, 0, "API error"), service.lookup("garbled"));
    }

    @Test
    void anUnreachableServerIsSkipped() {
        VirusTotalService service = new VirusTotalService(KEY, "http://127.0.0.1:1", Duration.ofSeconds(5), Duration.ofSeconds(60));

        assertEquals(ScanResult.Status.SKIPPED, service.lookup("clean").status());
    }

    @Test
    void withoutAKeyNothingIsAsked() {
        for (String key : new String[]{null, "", "  "}) {
            VirusTotalService service = new VirusTotalService(key, baseUrl, Duration.ofSeconds(5), Duration.ofSeconds(60));
            assertEquals(new ScanResult(ScanResult.Status.SKIPPED, 0, "no API key"), service.lookup("malicious"));
        }
        assertEquals(0, calls.get());
    }

    @Test
    void answersAreCachedButSkippedOnesAreNot() {
        VirusTotalService service = new VirusTotalService(KEY, baseUrl, Duration.ofSeconds(5), Duration.ofMillis(10));

        service.lookup("clean");
        service.lookup("clean");
        service.lookup("unknown");
        service.lookup("unknown");
        assertEquals(2, calls.get(), "a clean and an unknown answer are each asked for once");

        service.lookup("busy");
        service.lookup("busy");
        assertEquals(4, calls.get(), "a quota error is asked again next time");
    }

    @Test
    void neverMoreThanFourCallsInOneWindowAndTheRestAreSkipped() {
        VirusTotalService service = new VirusTotalService(KEY, baseUrl, Duration.ofMillis(200), Duration.ofSeconds(60));

        for (int i = 0; i < 4; i++) {
            assertEquals(ScanResult.Status.CLEAN, service.lookup("clean-" + i).status(), "call " + i);
        }
        long started = System.nanoTime();
        assertEquals(new ScanResult(ScanResult.Status.SKIPPED, 0, "rate limit"), service.lookup("clean-5"));

        assertEquals(4, calls.get(), "the fifth was not sent");
        assertTrue(System.nanoTime() - started >= Duration.ofMillis(150).toNanos(), "it waited for a permit first");
    }

    @Test
    void aWaitingCallGetsThePermitBackOnceTheWindowIsOver() {
        VirusTotalService service = new VirusTotalService(KEY, baseUrl, Duration.ofSeconds(5), Duration.ofMillis(300));

        for (int i = 0; i < 4; i++) {
            service.lookup("clean-" + i);
        }
        long started = System.nanoTime();
        assertEquals(ScanResult.Status.CLEAN, service.lookup("clean-5").status());

        assertEquals(5, calls.get());
        assertTrue(System.nanoTime() - started >= Duration.ofMillis(200).toNanos(), "it waited for the release");
    }
}
