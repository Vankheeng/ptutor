package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import com.ptutor.backend.dto.recommendation.WeightModel;
import com.ptutor.backend.entity.enums.WeightLearningMode;
import com.ptutor.backend.repository.LearningDataRepository;
import com.ptutor.backend.repository.RecommendationLearnedWeightsRepository;

import tools.jackson.databind.json.JsonMapper;

/** The nightly job and an administrator's "learn now" never run at the same time (step 43a). */
class WeightLearningServiceLockTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 27, 20, 30);

    @Test
    void aSecondRunWhileOneIsGoingOnDoesNothing() throws Exception {
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        WeightProvider provider = mock(WeightProvider.class);
        // Every model is OFF, so a run only asks for the modes; the first question blocks until released.
        when(provider.mode(WeightModel.A_S2T)).thenAnswer(invocation -> {
            inside.countDown();
            release.await(5, TimeUnit.SECONDS);
            return WeightLearningMode.OFF;
        });
        for (WeightModel model : List.of(WeightModel.A_T2S, WeightModel.B_S2T, WeightModel.B_T2S)) {
            when(provider.mode(model)).thenReturn(WeightLearningMode.OFF);
        }
        WeightLearningService service = new WeightLearningService(mock(LearningDataRepository.class),
                mock(RecommendationLearnedWeightsRepository.class), provider, new RecommendationSettings(),
                JsonMapper.builder().build(), mock(JdbcTemplate.class), mock(ApplicationEventPublisher.class),
                mock(PlatformTransactionManager.class));

        CompletableFuture<Optional<List<WeightLearningService.Outcome>>> first =
                CompletableFuture.supplyAsync(() -> service.learnAllIfIdle(NOW));
        assertThat(inside.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(service.learnAllIfIdle(NOW)).isEmpty();

        release.countDown();
        assertThat(first.get(5, TimeUnit.SECONDS)).isPresent();
        assertThat(service.learnAllIfIdle(NOW)).isPresent();
    }
}
