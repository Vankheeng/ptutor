package com.ptutor.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ptutor.backend.entity.AiEnrichmentJob;

/**
 * Two backend instances draining the queue at the same time must never get the same job, and
 * must not wait for each other. This test commits real rows (and deletes them afterwards): it
 * uses the RERANK_PREFETCH job type, which no worker in this application consumes, so the
 * scheduled EmbeddingWorker cannot interfere.
 */
@SpringBootTest
class AiJobQueueConcurrencyIntegrationTest {

    private static final String JOB_TYPE = "RERANK_PREFETCH";
    private static final int JOBS = 6;

    @Autowired AiEnrichmentJobRepository jobRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;

    private final List<UUID> jobIds = new ArrayList<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @BeforeEach
    void insertCommittedJobs() {
        Timestamp due = Timestamp.valueOf(LocalDateTime.now().minusMinutes(1));
        for (int index = 0; index < JOBS; index++) {
            UUID id = UUID.randomUUID();
            jobIds.add(id);
            jdbc.update("""
                    INSERT INTO ai_enrichment_jobs
                        (id, job_type, entity_type, entity_id, status, attempts, next_attempt_at, created_at, updated_at)
                    VALUES (?, ?, 'TEACHING_REQUEST', ?, 'PENDING', 0, ?, ?, ?)
                    """, id, JOB_TYPE, UUID.randomUUID(), due, due, due);
        }
    }

    @AfterEach
    void deleteJobs() {
        executor.shutdownNow();
        jobIds.forEach(id -> jdbc.update("DELETE FROM ai_enrichment_jobs WHERE id = ?", id));
    }

    @Test
    void concurrentWorkersLockDisjointJobsWithoutBlocking() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch firstHoldsLocks = new CountDownLatch(1);
        CountDownLatch secondIsDone = new CountDownLatch(1);

        Future<Set<UUID>> first = executor.submit(() -> transaction.execute(status -> {
            Set<UUID> locked = lock(4);
            firstHoldsLocks.countDown();
            // Keep the transaction (and its row locks) open until the second worker has finished.
            await(secondIsDone);
            return locked;
        }));
        assertThat(firstHoldsLocks.await(10, TimeUnit.SECONDS)).isTrue();

        Future<Set<UUID>> second = executor.submit(() -> transaction.execute(status -> lock(4)));
        Set<UUID> secondLocked = second.get(5, TimeUnit.SECONDS);
        secondIsDone.countDown();
        Set<UUID> firstLocked = first.get(10, TimeUnit.SECONDS);

        assertThat(firstLocked).hasSize(4);
        assertThat(secondLocked).as("the second worker skips locked rows instead of waiting").hasSize(2);
        assertThat(firstLocked).doesNotContainAnyElementsOf(secondLocked);
        Set<UUID> all = new HashSet<>(firstLocked);
        all.addAll(secondLocked);
        assertThat(all).containsExactlyInAnyOrderElementsOf(jobIds);
    }

    private Set<UUID> lock(int limit) {
        Set<UUID> ids = new HashSet<>();
        for (AiEnrichmentJob job : jobRepository.lockDueJobs(JOB_TYPE, LocalDateTime.now(), limit)) {
            ids.add(job.getId());
        }
        return ids;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
