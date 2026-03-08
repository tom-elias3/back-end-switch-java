package com.tom.backendswitch.loadtest;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

@Tag("loadtest")
@Testcontainers
class LoadTest {

    @Container
    static GenericContainer<?> app = new GenericContainer<>("backendswitch")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/actuator/health").forStatusCode(200).withStartupTimeout(Duration.ofSeconds(60)));

    @Test
    void run() throws Exception {
        String baseUrl = "http://localhost:" + app.getMappedPort(8080);
        int threads = Integer.getInteger("threads", 50);
        int durationSeconds = Integer.getInteger("durationSeconds", 30);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        HttpClient client = HttpClient.newBuilder()
                .executor(executor)
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
        AtomicLong errors = new AtomicLong();
        List<CompletableFuture<?>> futures = new ArrayList<>();
        Random random = new Random();

        long startMs = System.currentTimeMillis();
        long endMs = startMs + durationSeconds * 1000L;
        int i = 0;

        while (System.currentTimeMillis() < endMs) {
            final HttpRequest req = buildRequest(baseUrl, i++, random);
            final long reqStart = System.currentTimeMillis();
            CompletableFuture<?> f = client.sendAsync(req, HttpResponse.BodyHandlers.discarding())
                    .whenComplete((resp, ex) -> {
                        long latency = System.currentTimeMillis() - reqStart;
                        if (ex != null) {
                            errors.incrementAndGet();
                        } else {
                            latencies.add(latency);
                        }
                    });
            futures.add(f);
        }

        long actualDurationMs = System.currentTimeMillis() - startMs;

        // Drain all in-flight requests
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        executor.shutdown();

        List<Long> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);
        printStats(sorted, actualDurationMs, errors.get(), threads, durationSeconds);
    }

    private HttpRequest buildRequest(String baseUrl, int i, Random random) {
        boolean isGet = (i % 2 == 0);
        int subdomain = random.nextInt(20) + 1;
        int operation = random.nextInt(10) + 1;
        String url = "https://sub" + subdomain + ".tom.com/perform?operation=" + operation;

        String jwt;
        if (isGet) {
            // Fixed iat so pattern.1 expression logic evaluates correctly
            jwt = makeJwt(1516239022L);
        } else {
            // Random iat to exercise pattern.2 RANDOM:50
            jwt = makeJwt(System.currentTimeMillis() / 1000 + random.nextInt(10000));
        }

        String body = "{\"method\":\"" + (isGet ? "GET" : "POST") + "\","
                + "\"url\":\"" + url + "\","
                + "\"jsonPayload\":null,"
                + "\"headers\":{}}";

        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/decide"))
                .header("Content-Type", "application/json")
                .header("Authorization", jwt)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
    }

    private String makeJwt(long iat) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"iat\":" + iat + "}").getBytes(StandardCharsets.UTF_8));
        return "Bearer " + header + "." + payload + ".sig";
    }

    private void printStats(List<Long> latencies, long durationMs, long errorCount, int threads, int durationSeconds) {
        long total = latencies.size() + errorCount;
        double throughput = total / (durationMs / 1000.0);

        System.out.println();
        System.out.println("=== Load Test Results ===");
        System.out.printf("Duration:     %ds | Threads: %d%n", durationSeconds, threads);
        System.out.printf("Total:        %,d requests%n", total);
        System.out.printf("Throughput:   %.0f req/s%n", throughput);
        System.out.printf("Errors:       %d (%.2f%%)%n", errorCount, total > 0 ? errorCount * 100.0 / total : 0.0);

        if (!latencies.isEmpty()) {
            System.out.printf("Latency min:  %d ms%n", latencies.get(0));
            System.out.printf("Latency p50:  %d ms%n", percentile(latencies, 50));
            System.out.printf("Latency p95:  %d ms%n", percentile(latencies, 95));
            System.out.printf("Latency p99:  %d ms%n", percentile(latencies, 99));
            System.out.printf("Latency max:  %d ms%n", latencies.get(latencies.size() - 1));
        } else {
            System.out.println("Latency:      no successful responses");
        }
        System.out.println();
    }

    private long percentile(List<Long> sorted, int p) {
        int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
    }
}
