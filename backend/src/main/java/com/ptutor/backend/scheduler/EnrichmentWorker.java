package com.ptutor.backend.scheduler;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ptutor.backend.client.AiServiceClient;
import com.ptutor.backend.service.EmbeddingJobProcessor.ClaimedJob;
import com.ptutor.backend.service.EmbeddingJobProcessor.Outcome;
import com.ptutor.backend.service.EnrichmentJobProcessor;

/**
 * Drains ENRICH jobs (A1). When the AI service has no LLM configured or is down the jobs simply
 * wait: nothing is claimed and no attempt is consumed. Ranking works without tags in the meantime.
 */
@Component
public class EnrichmentWorker {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentWorker.class);

    private final EnrichmentJobProcessor processor;
    private final AiServiceClient aiServiceClient;
    private final boolean enabled;
    private final int batchSize;

    public EnrichmentWorker(
            EnrichmentJobProcessor processor,
            AiServiceClient aiServiceClient,
            @Value("${app.recommendation.enrichment.enabled:true}") boolean enabled,
            @Value("${app.recommendation.enrichment.batch-size:5}") int batchSize) {
        this.processor = processor;
        this.aiServiceClient = aiServiceClient;
        this.enabled = enabled;
        this.batchSize = batchSize;
    }

    @Scheduled(
            initialDelayString = "${app.recommendation.enrichment.worker-initial-delay-ms:20000}",
            fixedDelayString = "${app.recommendation.enrichment.worker-interval-ms:30000}")
    public void run() {
        if (!enabled) {
            return;
        }
        try {
            if (!processor.hasDueJobs()) {
                return;
            }
            if (!aiServiceClient.isLlmReady()) {
                log.debug("LLM is not available; enrichment jobs stay queued");
                return;
            }
            List<ClaimedJob> jobs = processor.claim(batchSize);
            Map<Outcome, Integer> outcomes = new EnumMap<>(Outcome.class);
            for (ClaimedJob job : jobs) {
                outcomes.merge(processor.process(job), 1, Integer::sum);
            }
            if (!jobs.isEmpty()) {
                log.info("Enrichment worker processed {} jobs: {}", jobs.size(), outcomes);
            }
        } catch (Exception exception) {
            log.warn("Enrichment worker run failed", exception);
        }
    }
}
