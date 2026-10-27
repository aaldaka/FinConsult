package com.example.finConsult.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Plain JUnit, no Spring. Uses a fake key: VirusTotal answers 401, which still takes a permit, so the limiter is exercised
// without using real quota. Needs no real key. Takes about 65 seconds.
class VirusTotalServiceTest {

    @Test
    void withoutAKeyTheScanIsSkipped() {
        ScanResult result = new VirusTotalService("").lookup("a".repeat(64));

        assertEquals(ScanResult.Status.SKIPPED, result.status());
        assertEquals("no API key", result.note());
    }

    // 6 lookups at once, 4 permits a minute: 4 go straight away, the 5th and 6th wait for a permit to come back
    @Test
    void neverMoreThanFourCallsPerMinute() {
        VirusTotalService service = new VirusTotalService("fake-key-for-the-limiter-test");
        ExecutorService pool = Executors.newFixedThreadPool(6);
        long start = System.currentTimeMillis();

        List<CompletableFuture<Long>> calls = IntStream.range(0, 6)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                    service.lookup((UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "").substring(0, 64));
                    return (System.currentTimeMillis() - start) / 1000;   // seconds until this lookup finished
                }, pool))
                .toList();

        List<Long> seconds = calls.stream().map(CompletableFuture::join).sorted().toList();
        pool.shutdown();
        System.out.println("Lookups finished after (seconds): " + seconds);

        assertTrue(seconds.get(3) < 40, "the first 4 should not wait for a permit");
        assertTrue(seconds.get(4) >= 55, "the 5th must wait for a permit to come back, finished after " + seconds.get(4));
    }
}
