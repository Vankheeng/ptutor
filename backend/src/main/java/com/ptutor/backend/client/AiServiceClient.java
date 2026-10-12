package com.ptutor.backend.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * HTTP client of the internal AI service. Every call has a timeout and never throws: when the
 * service is down, slow or answers something unexpected the result is empty and the caller
 * falls back (lower quality, no error for the user).
 */
@Component
public class AiServiceClient {

    public static final int EMBEDDING_DIMENSIONS = 1024;

    private static final Logger log = LoggerFactory.getLogger(AiServiceClient.class);
    private static final String TOKEN_HEADER = "X-Internal-Token";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final boolean enabled;
    private final String baseUrl;
    private final String internalToken;
    private final Duration embedTimeout;
    private final Duration healthTimeout;

    @Autowired
    public AiServiceClient(
            ObjectMapper objectMapper,
            @Value("${app.ai-service.enabled:true}") boolean enabled,
            @Value("${app.ai-service.base-url:http://127.0.0.1:8000}") String baseUrl,
            @Value("${app.ai-service.internal-token:}") String internalToken,
            @Value("${app.ai-service.connect-timeout-ms:500}") long connectTimeoutMs,
            @Value("${app.ai-service.embed-timeout-ms:1000}") long embedTimeoutMs,
            @Value("${app.ai-service.health-timeout-ms:500}") long healthTimeoutMs) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.internalToken = internalToken == null ? "" : internalToken.strip();
        this.embedTimeout = Duration.ofMillis(embedTimeoutMs);
        this.healthTimeout = Duration.ofMillis(healthTimeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /** True when the service answers and its embedding model is loaded. */
    public boolean isReady() {
        return "ready".equals(health("status"));
    }

    /** True when the service answers and has an LLM configured (an API key is set on its side). */
    public boolean isLlmReady() {
        return "ready".equals(health("llm"));
    }

    /** True when the service answers and its cross-encoder is loaded. */
    public boolean isRerankReady() {
        return "ready".equals(health("rerank"));
    }

    /**
     * Relevance in [0, 1] of each document to the query, in the same order (design 06, 6.9).
     * Empty when the service is disabled, unreachable, too slow, has no reranking model, or the
     * answer is unusable. The caller then keeps the order it already has.
     */
    public Optional<RerankResult> rerank(String query, List<String> documents, Duration timeout) {
        if (!enabled || query == null || query.isBlank() || documents == null || documents.isEmpty()) {
            return Optional.empty();
        }
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + "/rerank"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(Map.of("query", query, "documents", documents)),
                            StandardCharsets.UTF_8));
            if (!internalToken.isEmpty()) {
                request.header(TOKEN_HEADER, internalToken);
            }
            HttpResponse<String> response = httpClient.send(
                    request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.warn("AI service /rerank answered HTTP {}", response.statusCode());
                return Optional.empty();
            }
            JsonNode body = objectMapper.readTree(response.body());
            JsonNode scores = body.path("scores");
            String model = body.path("model").asString("");
            if (!scores.isArray() || scores.size() != documents.size() || model.isBlank()) {
                log.warn("AI service /rerank returned {} scores for {} documents", scores.size(), documents.size());
                return Optional.empty();
            }
            List<Double> values = new ArrayList<>(documents.size());
            for (JsonNode score : scores) {
                if (!score.isNumber() || score.doubleValue() < 0 || score.doubleValue() > 1) {
                    log.warn("AI service /rerank returned a score outside [0, 1]");
                    return Optional.empty();
                }
                values.add(score.doubleValue());
            }
            return Optional.of(new RerankResult(model, values));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception exception) {
            log.warn("AI service /rerank failed: {}", exception.toString());
            return Optional.empty();
        }
    }

    private String health(String field) {
        if (!enabled) {
            return "";
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/health"))
                    .timeout(healthTimeout)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.statusCode() == 200
                    ? objectMapper.readTree(response.body()).path(field).asString("") : "";
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return "";
        } catch (Exception exception) {
            log.debug("AI service health check failed: {}", exception.toString());
            return "";
        }
    }

    /**
     * Asks the LLM to extract tags and weekly availability from free text (design 04, 4.4).
     * Empty when the service is disabled, unreachable, too slow, has no LLM, or the answer is
     * unusable. The caller still has to validate every item against its own rules.
     */
    public Optional<EnrichResult> enrich(EnrichRequest enrichRequest, Duration timeout) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + "/enrich"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(enrichRequest), StandardCharsets.UTF_8));
            if (!internalToken.isEmpty()) {
                request.header(TOKEN_HEADER, internalToken);
            }
            HttpResponse<String> response = httpClient.send(
                    request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.warn("AI service /enrich answered HTTP {}", response.statusCode());
                return Optional.empty();
            }
            JsonNode body = objectMapper.readTree(response.body());
            if (!body.path("schedule").isArray() || !body.path("tags").isArray()) {
                log.warn("AI service /enrich returned an unexpected body");
                return Optional.empty();
            }
            List<ExtractedSlot> schedule = new ArrayList<>();
            for (JsonNode slot : body.path("schedule")) {
                schedule.add(new ExtractedSlot(slot.path("dayOfWeek").asInt(0), slot.path("start").asString(""),
                        slot.path("end").asString(""), slot.path("confidence").asDouble(0)));
            }
            List<ExtractedTag> tags = new ArrayList<>();
            for (JsonNode tag : body.path("tags")) {
                tags.add(new ExtractedTag(tag.path("group").asString(""), tag.path("code").asString(""),
                        tag.path("confidence").asDouble(0)));
            }
            return Optional.of(new EnrichResult(body.path("model").asString(""), schedule, tags));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception exception) {
            log.warn("AI service /enrich failed: {}", exception.toString());
            return Optional.empty();
        }
    }

    /** Embeds with the default timeout, meant for the interactive search path. */
    public Optional<EmbeddingResult> embed(List<String> texts) {
        return embed(texts, embedTimeout);
    }

    /**
     * One unit-length vector of {@value #EMBEDDING_DIMENSIONS} floats per text, in the same order.
     * Empty when the service is disabled, unreachable, too slow, or returns an unusable answer.
     */
    public Optional<EmbeddingResult> embed(List<String> texts, Duration timeout) {
        if (!enabled || texts == null || texts.isEmpty()) {
            return Optional.empty();
        }
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + "/embed"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(Map.of("texts", texts)), StandardCharsets.UTF_8));
            if (!internalToken.isEmpty()) {
                request.header(TOKEN_HEADER, internalToken);
            }
            HttpResponse<String> response = httpClient.send(
                    request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.warn("AI service /embed answered HTTP {}", response.statusCode());
                return Optional.empty();
            }
            return parseEmbeddings(objectMapper.readTree(response.body()), texts.size());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception exception) {
            log.warn("AI service /embed failed: {}", exception.toString());
            return Optional.empty();
        }
    }

    private Optional<EmbeddingResult> parseEmbeddings(JsonNode body, int expectedCount) {
        JsonNode embeddings = body.path("embeddings");
        String model = body.path("model").asString("");
        if (!embeddings.isArray() || embeddings.size() != expectedCount || model.isBlank()) {
            log.warn("AI service /embed returned {} vectors for {} texts", embeddings.size(), expectedCount);
            return Optional.empty();
        }
        List<float[]> vectors = new ArrayList<>(expectedCount);
        for (JsonNode embedding : embeddings) {
            if (!embedding.isArray() || embedding.size() != EMBEDDING_DIMENSIONS) {
                log.warn("AI service /embed returned a vector of {} dimensions, expected {}",
                        embedding.size(), EMBEDDING_DIMENSIONS);
                return Optional.empty();
            }
            float[] vector = new float[EMBEDDING_DIMENSIONS];
            for (int index = 0; index < EMBEDDING_DIMENSIONS; index++) {
                JsonNode value = embedding.get(index);
                if (!value.isNumber()) {
                    log.warn("AI service /embed returned a non-numeric vector component");
                    return Optional.empty();
                }
                vector[index] = value.floatValue();
            }
            vectors.add(vector);
        }
        return Optional.of(new EmbeddingResult(model, vectors));
    }

    /**
     * Asks the LLM for topic ideas for one subject. Empty when the service or the LLM is unavailable.
     * The caller validates the result and shows it to an administrator; nothing is stored here.
     */
    public Optional<TagSuggestions> suggestTags(SuggestTagsRequest suggestRequest, Duration timeout) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + "/suggest-tags"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(suggestRequest), StandardCharsets.UTF_8));
            if (!internalToken.isEmpty()) {
                request.header(TOKEN_HEADER, internalToken);
            }
            HttpResponse<String> response = httpClient.send(
                    request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.warn("AI service /suggest-tags answered HTTP {}", response.statusCode());
                return Optional.empty();
            }
            JsonNode body = objectMapper.readTree(response.body());
            if (!body.path("tags").isArray()) {
                log.warn("AI service /suggest-tags returned an unexpected body");
                return Optional.empty();
            }
            List<SuggestedTag> tags = new ArrayList<>();
            for (JsonNode tag : body.path("tags")) {
                JsonNode parent = tag.path("parentCode");
                tags.add(new SuggestedTag(tag.path("code").asString(""), tag.path("name").asString(""),
                        tag.path("description").asString(""), parent.isString() ? parent.asString() : null));
            }
            return Optional.of(new TagSuggestions(body.path("model").asString(""), tags));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception exception) {
            log.warn("AI service /suggest-tags failed: {}", exception.toString());
            return Optional.empty();
        }
    }

    /** Body of {@code POST /suggest-tags}. */
    public record SuggestTagsRequest(String subject, List<VocabularyTag> existing, List<String> samples, int maxTags) {
    }

    public record TagSuggestions(String model, List<SuggestedTag> tags) {
    }

    public record SuggestedTag(String code, String name, String description, String parentCode) {
    }

    /** Body of {@code POST /enrich}; component names are the JSON field names of the contract. */
    public record EnrichRequest(
            String entityType, String subject, List<String> grades, Map<String, String> fields,
            Map<String, List<VocabularyTag>> vocabulary, List<String> extract) {
    }

    public record VocabularyTag(String code, String name) {
    }

    public record EnrichResult(String model, List<ExtractedSlot> schedule, List<ExtractedTag> tags) {
    }

    /** {@code dayOfWeek} is ISO-8601 (1 = Monday ... 7 = Sunday); times are "HH:mm" strings as received. */
    public record ExtractedSlot(int dayOfWeek, String start, String end, double confidence) {
    }

    public record ExtractedTag(String group, String code, double confidence) {
    }

    /** {@code model} is stored as {@code embedding_model_version} next to each vector. */
    public record RerankResult(String model, List<Double> scores) {
    }

    public record EmbeddingResult(String model, List<float[]> vectors) {
    }
}
