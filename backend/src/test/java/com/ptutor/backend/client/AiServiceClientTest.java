package com.ptutor.backend.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class AiServiceClientTest {

    private static final String TOKEN = "secret-token";

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final AtomicInteger embedCalls = new AtomicInteger();
    private final AtomicReference<String> receivedToken = new AtomicReference<>();
    private final AtomicReference<JsonNode> receivedBody = new AtomicReference<>();

    private HttpServer server;
    private volatile int embedStatus = 200;
    private volatile String embedBody = "";
    private volatile long embedDelayMs = 0;
    private volatile String healthBody = "{\"status\":\"ready\",\"embed_model\":\"bge-m3-int8\"}";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/embed", exchange -> {
            embedCalls.incrementAndGet();
            receivedToken.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            receivedBody.set(objectMapper.readTree(
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            sleep(embedDelayMs);
            respond(exchange, embedStatus, embedBody);
        });
        server.createContext("/health", exchange -> respond(exchange, 200, healthBody));
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void embedReturnsVectorsInOrderAndSendsTokenAndTexts() {
        embedBody = embeddings("bge-m3-int8", vector(1024, 0.5f), vector(1024, -0.25f));

        var result = client(true, TOKEN).embed(List.of("Toán 9", "IELTS 6.5"));

        assertThat(result).isPresent();
        assertThat(result.get().model()).isEqualTo("bge-m3-int8");
        assertThat(result.get().vectors()).hasSize(2);
        assertThat(result.get().vectors().get(0)).hasSize(1024).containsOnly(0.5f);
        assertThat(result.get().vectors().get(1)).containsOnly(-0.25f);
        assertThat(receivedToken.get()).isEqualTo(TOKEN);
        assertThat(receivedBody.get().path("texts").get(0).asString()).isEqualTo("Toán 9");
        assertThat(receivedBody.get().path("texts").get(1).asString()).isEqualTo("IELTS 6.5");
    }

    @Test
    void embedFallsBackToEmptyOnServerError() {
        embedStatus = 503;
        embedBody = "{\"detail\":\"Embedding model is loading\"}";

        assertThat(client(true, TOKEN).embed(List.of("a"))).isEmpty();
    }

    @Test
    void embedFallsBackToEmptyWhenServiceIsTooSlow() {
        embedBody = embeddings("bge-m3-int8", vector(1024, 0.5f));
        embedDelayMs = 1_500;

        long started = System.nanoTime();
        var result = client(true, TOKEN).embed(List.of("a"), Duration.ofMillis(200));

        assertThat(result).isEmpty();
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(1_200));
    }

    @Test
    void embedFallsBackToEmptyWhenServiceIsUnreachable() {
        server.stop(0);

        assertThat(client(true, TOKEN).embed(List.of("a"))).isEmpty();
        assertThat(client(true, TOKEN).isReady()).isFalse();
    }

    @Test
    void embedRejectsVectorsWithWrongDimension() {
        embedBody = embeddings("bge-m3-int8", vector(1536, 0.5f));

        assertThat(client(true, TOKEN).embed(List.of("a"))).isEmpty();
    }

    @Test
    void embedRejectsAnswerWithWrongNumberOfVectors() {
        embedBody = embeddings("bge-m3-int8", vector(1024, 0.5f));

        assertThat(client(true, TOKEN).embed(List.of("a", "b"))).isEmpty();
    }

    @Test
    void embedRejectsMalformedAnswer() {
        embedBody = "not json";

        assertThat(client(true, TOKEN).embed(List.of("a"))).isEmpty();
    }

    @Test
    void doesNotCallServiceWhenDisabledOrNothingToEmbed() {
        assertThat(client(false, TOKEN).embed(List.of("a"))).isEmpty();
        assertThat(client(false, TOKEN).isReady()).isFalse();
        assertThat(client(true, TOKEN).embed(List.of())).isEmpty();

        assertThat(embedCalls).hasValue(0);
    }

    @Test
    void isReadyOnlyWhenModelIsLoaded() {
        assertThat(client(true, TOKEN).isReady()).isTrue();

        healthBody = "{\"status\":\"loading\",\"embed_model\":\"bge-m3-int8\"}";

        assertThat(client(true, TOKEN).isReady()).isFalse();
    }

    private AiServiceClient client(boolean enabled, String token) {
        return new AiServiceClient(
                objectMapper, enabled, "http://127.0.0.1:" + server.getAddress().getPort() + "/", token,
                300, 1_000, 300);
    }

    private static String embeddings(String model, String... vectors) {
        return "{\"model\":\"" + model + "\",\"dimensions\":1024,\"embeddings\":["
                + String.join(",", vectors) + "]}";
    }

    private static String vector(int dimensions, float value) {
        return IntStream.range(0, dimensions)
                .mapToObj(index -> Float.toString(value))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        } catch (IOException ignored) {
            // The client gave up waiting (timeout test).
        }
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
