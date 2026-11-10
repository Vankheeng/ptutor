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
    private volatile int enrichStatus = 200;
    private volatile String enrichBody = "";
    private final AtomicReference<JsonNode> receivedEnrichBody = new AtomicReference<>();
    private volatile int rerankStatus = 200;
    private volatile String rerankBody = "";
    private volatile long rerankDelayMs = 0;
    private final AtomicReference<JsonNode> receivedRerankBody = new AtomicReference<>();
    private volatile int parseStatus = 200;
    private volatile String parseBody = "";
    private final AtomicReference<JsonNode> receivedParseBody = new AtomicReference<>();
    private volatile int suggestStatus = 200;
    private volatile String suggestBody = "";
    private final AtomicReference<JsonNode> receivedSuggestBody = new AtomicReference<>();

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
        server.createContext("/enrich", exchange -> {
            receivedToken.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            receivedEnrichBody.set(objectMapper.readTree(
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            respond(exchange, enrichStatus, enrichBody);
        });
        server.createContext("/rerank", exchange -> {
            receivedToken.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            receivedRerankBody.set(objectMapper.readTree(
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            sleep(rerankDelayMs);
            respond(exchange, rerankStatus, rerankBody);
        });
        server.createContext("/parse-query", exchange -> {
            receivedToken.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            receivedParseBody.set(objectMapper.readTree(
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            respond(exchange, parseStatus, parseBody);
        });
        server.createContext("/suggest-tags", exchange -> {
            receivedToken.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            receivedSuggestBody.set(objectMapper.readTree(
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            respond(exchange, suggestStatus, suggestBody);
        });
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

    @Test
    void enrichSendsTheContractAndParsesScheduleAndTags() {
        enrichBody = """
                {"model": "gpt-6-luna",
                 "schedule": [{"dayOfWeek": 1, "start": "19:00", "end": "21:00", "confidence": 0.9}],
                 "tags": [{"group": "GOAL", "code": "thi_vao_10", "role": "SELF", "confidence": 0.95}]}
                """;
        AiServiceClient.EnrichRequest request = new AiServiceClient.EnrichRequest(
                "STUDYING_REQUEST", "Toán", List.of("Lớp 9"), java.util.Map.of("description", "Yếu hình"),
                java.util.Map.of("GOAL", List.of(new AiServiceClient.VocabularyTag("thi_vao_10", "Thi vào lớp 10"))),
                List.of("schedule", "tags"));

        var result = client(true, TOKEN).enrich(request, Duration.ofSeconds(2));

        assertThat(result).isPresent();
        assertThat(result.get().model()).isEqualTo("gpt-6-luna");
        assertThat(result.get().schedule()).containsExactly(
                new AiServiceClient.ExtractedSlot(1, "19:00", "21:00", 0.9));
        assertThat(result.get().tags()).containsExactly(new AiServiceClient.ExtractedTag("GOAL", "thi_vao_10", 0.95));
        assertThat(receivedToken.get()).isEqualTo(TOKEN);
        JsonNode sent = receivedEnrichBody.get();
        assertThat(sent.path("entityType").asString()).isEqualTo("STUDYING_REQUEST");
        assertThat(sent.path("subject").asString()).isEqualTo("Toán");
        assertThat(sent.path("grades").get(0).asString()).isEqualTo("Lớp 9");
        assertThat(sent.path("fields").path("description").asString()).isEqualTo("Yếu hình");
        assertThat(sent.path("vocabulary").path("GOAL").get(0).path("code").asString()).isEqualTo("thi_vao_10");
        assertThat(sent.path("extract").get(1).asString()).isEqualTo("tags");
    }

    @Test
    void enrichFallsBackToEmptyWhenTheLlmIsMissingFailingOrAnswersNonsense() {
        AiServiceClient.EnrichRequest request = new AiServiceClient.EnrichRequest(
                "TUTOR", null, List.of(), java.util.Map.of("introduction", "x"), java.util.Map.of(), List.of("tags"));

        enrichStatus = 503;
        assertThat(client(true, TOKEN).enrich(request, Duration.ofSeconds(2))).isEmpty();
        enrichStatus = 502;
        assertThat(client(true, TOKEN).enrich(request, Duration.ofSeconds(2))).isEmpty();
        enrichStatus = 200;
        enrichBody = "{\"model\":\"x\",\"tags\":\"oops\"}";
        assertThat(client(true, TOKEN).enrich(request, Duration.ofSeconds(2))).isEmpty();
        assertThat(client(false, TOKEN).enrich(request, Duration.ofSeconds(2))).isEmpty();
    }

    @Test
    void rerankSendsQueryAndDocumentsAndReturnsScoresInOrder() {
        rerankBody = "{\"model\":\"bge-reranker-v2-m3-int8\",\"scores\":[0.91,0.02]}";

        var result = client(true, TOKEN).rerank("Cần gia sư Toán", List.of("Dạy Toán 9", "Dạy Văn"),
                Duration.ofSeconds(2));

        assertThat(result).isPresent();
        assertThat(result.get().model()).isEqualTo("bge-reranker-v2-m3-int8");
        assertThat(result.get().scores()).containsExactly(0.91, 0.02);
        assertThat(receivedToken.get()).isEqualTo(TOKEN);
        assertThat(receivedRerankBody.get().path("query").asString()).isEqualTo("Cần gia sư Toán");
        assertThat(receivedRerankBody.get().path("documents").get(1).asString()).isEqualTo("Dạy Văn");
    }

    @Test
    void rerankIsEmptyWhenTheModelIsMissingTooSlowOrAnswersNonsense() {
        List<String> documents = List.of("Dạy Toán 9", "Dạy Văn");

        rerankStatus = 503;
        assertThat(client(true, TOKEN).rerank("q", documents, Duration.ofSeconds(2))).isEmpty();
        rerankStatus = 200;
        rerankBody = "{\"model\":\"m\",\"scores\":[0.9]}";
        assertThat(client(true, TOKEN).rerank("q", documents, Duration.ofSeconds(2))).as("one score missing").isEmpty();
        rerankBody = "{\"model\":\"m\",\"scores\":[0.9,1.7]}";
        assertThat(client(true, TOKEN).rerank("q", documents, Duration.ofSeconds(2))).as("score above 1").isEmpty();
        rerankBody = "{\"model\":\"m\",\"scores\":[0.9,0.1]}";
        rerankDelayMs = 600;
        assertThat(client(true, TOKEN).rerank("q", documents, Duration.ofMillis(150))).as("too slow").isEmpty();
        rerankDelayMs = 0;
        assertThat(client(false, TOKEN).rerank("q", documents, Duration.ofSeconds(2))).isEmpty();
        assertThat(client(true, TOKEN).rerank("q", List.of(), Duration.ofSeconds(2))).isEmpty();
    }

    @Test
    void rerankIsReadyOnlyWhenTheServiceReportsALoadedReranker() {
        assertThat(client(true, TOKEN).isRerankReady()).as("old service without the rerank field").isFalse();
        healthBody = "{\"status\":\"ready\",\"embed_model\":\"bge-m3-int8\",\"rerank\":\"loading\"}";
        assertThat(client(true, TOKEN).isRerankReady()).isFalse();
        healthBody = "{\"status\":\"ready\",\"embed_model\":\"bge-m3-int8\",\"rerank\":\"ready\"}";
        assertThat(client(true, TOKEN).isRerankReady()).isTrue();
        assertThat(client(false, TOKEN).isRerankReady()).isFalse();
    }

    @Test
    void parseQuerySendsTheCatalogueAndReturnsTheAnswerAsItIs() {
        parseBody = "{\"model\":\"gpt-6-luna\",\"subject\":\"Toán\",\"area\":{\"scope\":null,\"districts\":[]},"
                + "\"price\":{\"min\":null,\"max\":200000}}";
        AiServiceClient.ParseQueryRequest request = new AiServiceClient.ParseQueryRequest(
                "Gia sư Toán dưới 200k", "STUDENT_TO_TUTOR",
                new AiServiceClient.ParseQueryCatalog(List.of("Toán"), List.of("Lớp 9"), List.of("Cầu Giấy")),
                java.util.Map.of("STYLE", List.of(new AiServiceClient.VocabularyTag("kien_nhan", "Kiên nhẫn"))));

        var result = client(true, TOKEN).parseQuery(request, Duration.ofSeconds(2));

        assertThat(result).isPresent();
        assertThat(result.get().path("subject").asString()).isEqualTo("Toán");
        assertThat(result.get().path("price").path("max").asInt()).isEqualTo(200000);
        assertThat(receivedToken.get()).isEqualTo(TOKEN);
        JsonNode sent = receivedParseBody.get();
        assertThat(sent.path("text").asString()).isEqualTo("Gia sư Toán dưới 200k");
        assertThat(sent.path("direction").asString()).isEqualTo("STUDENT_TO_TUTOR");
        assertThat(sent.path("catalog").path("districtsInUserProvince").get(0).asString()).isEqualTo("Cầu Giấy");
        assertThat(sent.path("vocabulary").path("STYLE").get(0).path("code").asString()).isEqualTo("kien_nhan");

        parseStatus = 502;
        assertThat(client(true, TOKEN).parseQuery(request, Duration.ofSeconds(2))).isEmpty();
        parseStatus = 200;
        parseBody = "[]";
        assertThat(client(true, TOKEN).parseQuery(request, Duration.ofSeconds(2))).isEmpty();
        assertThat(client(false, TOKEN).parseQuery(request, Duration.ofSeconds(2))).isEmpty();
    }

    @Test
    void suggestTagsSendsTheContractAndParsesSuggestions() {
        suggestBody = """
                {"model": "gpt-6-luna", "tags": [
                  {"code": "di_truyen", "name": "Di truyền học", "description": "Gen, ADN", "parentCode": null},
                  {"code": "lai_mot_cap", "name": "Lai một cặp", "description": "", "parentCode": "di_truyen"}]}
                """;
        AiServiceClient.SuggestTagsRequest request = new AiServiceClient.SuggestTagsRequest(
                "Sinh học", List.of(new AiServiceClient.VocabularyTag("sinh_thai", "Sinh thái")),
                List.of("Sinh 9 di truyền"), 5);

        var result = client(true, TOKEN).suggestTags(request, Duration.ofSeconds(2));

        assertThat(result).isPresent();
        assertThat(result.get().model()).isEqualTo("gpt-6-luna");
        assertThat(result.get().tags()).containsExactly(
                new AiServiceClient.SuggestedTag("di_truyen", "Di truyền học", "Gen, ADN", null),
                new AiServiceClient.SuggestedTag("lai_mot_cap", "Lai một cặp", "", "di_truyen"));
        assertThat(receivedToken.get()).isEqualTo(TOKEN);
        JsonNode sent = receivedSuggestBody.get();
        assertThat(sent.path("subject").asString()).isEqualTo("Sinh học");
        assertThat(sent.path("existing").get(0).path("code").asString()).isEqualTo("sinh_thai");
        assertThat(sent.path("samples").get(0).asString()).isEqualTo("Sinh 9 di truyền");
        assertThat(sent.path("maxTags").asInt()).isEqualTo(5);
    }

    @Test
    void suggestTagsIsEmptyWhenTheLlmIsMissingFailingOrAnswersNonsense() {
        AiServiceClient.SuggestTagsRequest request =
                new AiServiceClient.SuggestTagsRequest("Sinh học", List.of(), List.of(), 5);

        suggestStatus = 503;
        assertThat(client(true, TOKEN).suggestTags(request, Duration.ofSeconds(2))).isEmpty();
        suggestStatus = 200;
        suggestBody = "{\"model\":\"x\",\"tags\":\"oops\"}";
        assertThat(client(true, TOKEN).suggestTags(request, Duration.ofSeconds(2))).isEmpty();
        assertThat(client(false, TOKEN).suggestTags(request, Duration.ofSeconds(2))).isEmpty();
    }

    @Test
    void llmIsReadyOnlyWhenTheServiceReportsAConfiguredLlm() {
        assertThat(client(true, TOKEN).isLlmReady()).as("old service without the llm field").isFalse();

        healthBody = "{\"status\":\"loading\",\"llm\":\"ready\",\"llm_model\":\"gpt-6-luna\"}";
        assertThat(client(true, TOKEN).isLlmReady()).isTrue();
        assertThat(client(true, TOKEN).isReady()).isFalse();

        healthBody = "{\"status\":\"ready\",\"llm\":\"disabled\"}";
        assertThat(client(true, TOKEN).isLlmReady()).isFalse();
        assertThat(client(false, TOKEN).isLlmReady()).isFalse();
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
