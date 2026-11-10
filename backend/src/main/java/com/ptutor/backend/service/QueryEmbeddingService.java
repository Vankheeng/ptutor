package com.ptutor.backend.service;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.ptutor.backend.client.AiServiceClient;

/**
 * Embeds the searcher's demand text. Vectors are cached by the hash of the text, so repeating a
 * search (or paging through it) does not call the AI service again. An empty result means the
 * caller must fall back, never that the search fails.
 */
@Service
public class QueryEmbeddingService {

    private final AiServiceClient aiServiceClient;
    private final EmbeddingTextBuilder textBuilder;
    private final Duration timeout;
    private final Map<String, float[]> cache;

    public QueryEmbeddingService(
            AiServiceClient aiServiceClient, EmbeddingTextBuilder textBuilder, RecommendationSettings settings) {
        this.aiServiceClient = aiServiceClient;
        this.textBuilder = textBuilder;
        this.timeout = Duration.ofMillis(settings.getQueryEmbeddingTimeoutMs());
        int capacity = Math.max(1, settings.getQueryEmbeddingCacheSize());
        this.cache = Collections.synchronizedMap(new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
                return size() > capacity;
            }
        });
    }

    /** Where the vector a search ranks with came from; stored in the log as {@code queryVector}. */
    public enum Source {
        /** The demand text was embedded. */
        QUERY,
        /**
         * The text could not be embedded in time (AI service down or busy), so the profile vector was used: the
         * ranking then reads the profile, not this search. Counted apart in the evaluation (design 13, 13.2).
         */
        PROFILE_FALLBACK,
        /** No text to embed: the profile vector is the intended input. */
        PROFILE
    }

    public record SearchVector(float[] vector, Source source) {
    }

    /** The vector of a search: its text, else the searcher's profile vector (which may be null). */
    public SearchVector forSearch(String text, float[] profileVector) {
        if (text == null || text.isBlank()) {
            return new SearchVector(profileVector, Source.PROFILE);
        }
        return embed(text).map(vector -> new SearchVector(vector, Source.QUERY))
                .orElseGet(() -> new SearchVector(profileVector, Source.PROFILE_FALLBACK));
    }

    public Optional<float[]> embed(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String key = textBuilder.hash(text);
        float[] cached = cache.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<float[]> vector = aiServiceClient.embed(List.of(text), timeout)
                .map(result -> result.vectors().get(0));
        vector.ifPresent(value -> cache.put(key, value));
        return vector;
    }
}
