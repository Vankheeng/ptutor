package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ptutor.backend.entity.RecommendationExposureStats;
import com.ptutor.backend.entity.RecommendationExposureStatsId;
import com.ptutor.backend.entity.enums.RecommendationCandidateType;
import com.ptutor.backend.repository.RecommendationExposureStatsRepository;
import com.ptutor.backend.service.ExplorationService.Result;

class ExplorationServiceTest {

    private static final RecommendationCandidateType TYPE = RecommendationCandidateType.TEACHING_REQUEST;

    private final RecommendationExposureStatsRepository statsRepository = mock(RecommendationExposureStatsRepository.class);
    private final RecommendationSettings settings = new RecommendationSettings();
    private final ExplorationService service = new ExplorationService(statsRepository, settings, new Random(7));

    private record Posting(UUID id, double score) {
    }

    @Test
    void givesPlacesFiveAndTwelveToGoodPostingsFromBelowTheFirstPage() {
        List<Posting> ranked = ranked(30, 0.80, 0.005);

        Result<Posting> result = service.apply(ranked, TYPE, Posting::id, Posting::score);

        assertThat(result.ranked()).hasSize(30).doesNotHaveDuplicates();
        assertThat(result.explored()).hasSize(2)
                .containsExactlyInAnyOrder(result.ranked().get(4).id(), result.ranked().get(11).id());
        assertThat(ranked.indexOf(result.ranked().get(4))).as("came from below the first page").isGreaterThanOrEqualTo(20);
        assertThat(ranked.indexOf(result.ranked().get(11))).isGreaterThanOrEqualTo(20);
        assertThat(result.ranked().subList(0, 4)).as("the first four places are never touched")
                .containsExactlyElementsOf(ranked.subList(0, 4));
        // Everything else keeps its relative order.
        List<Posting> others = new ArrayList<>(result.ranked());
        others.remove(11);
        others.remove(4);
        List<Posting> expected = new ArrayList<>(ranked);
        expected.removeIf(posting -> result.explored().contains(posting.id()));
        assertThat(others).containsExactlyElementsOf(expected);
    }

    @Test
    void leavesTheRankingAloneWhenThereAreTooFewResultsOrNothingIsGoodEnough() {
        List<Posting> few = ranked(25, 0.80, 0.005);
        assertThat(service.apply(few, TYPE, Posting::id, Posting::score).ranked()).isSameAs(few);
        verifyNoInteractions(statsRepository);

        // From rank 21 on the scores are below 80 % of the score at rank 20.
        List<Posting> steep = new ArrayList<>(ranked(20, 0.80, 0.0));
        steep.addAll(ranked(10, 0.50, 0.0));
        Result<Posting> result = service.apply(steep, TYPE, Posting::id, Posting::score);
        assertThat(result.ranked()).isSameAs(steep);
        assertThat(result.explored()).isEmpty();

        ReflectionTestUtils.setField(settings, "explorationEnabled", false);
        List<Posting> many = ranked(30, 0.80, 0.005);
        assertThat(service.apply(many, TYPE, Posting::id, Posting::score).explored()).isEmpty();
    }

    @Test
    void neverPicksAPostingThatWasShownMoreThanMostOthers() {
        List<Posting> ranked = ranked(30, 0.80, 0.005);
        // Rank 21 was shown a lot this week; every other posting was shown twice.
        Map<UUID, int[]> shown = new HashMap<>();
        ranked.forEach(posting -> shown.put(posting.id(), new int[] { 2, 0, 2, 0 }));
        shown.put(ranked.get(20).id(), new int[] { 40, 1, 40, 1 });
        stats(shown);

        for (int round = 0; round < 50; round++) {
            assertThat(service.apply(ranked, TYPE, Posting::id, Posting::score).explored())
                    .doesNotContain(ranked.get(20).id());
        }
    }

    @Test
    void prefersPostingsThatGetOpenedWhenShownOverOnesThatDoNot() {
        // Two candidates with the same score: one is opened almost every time, the other almost never.
        List<Posting> ranked = new ArrayList<>(ranked(20, 0.80, 0.0));
        Posting popular = new Posting(UUID.randomUUID(), 0.75);
        Posting ignored = new Posting(UUID.randomUUID(), 0.75);
        ranked.add(popular);
        ranked.add(ignored);
        ranked.addAll(ranked(6, 0.10, 0.0));
        Map<UUID, int[]> shown = new HashMap<>();
        shown.put(popular.id(), new int[] { 0, 0, 60, 45 });
        shown.put(ignored.id(), new int[] { 0, 0, 60, 2 });
        stats(shown);
        ReflectionTestUtils.setField(settings, "explorationSlots", new int[] { 5 });

        int popularWins = 0;
        for (int round = 0; round < 200; round++) {
            if (service.apply(ranked, TYPE, Posting::id, Posting::score).explored().contains(popular.id())) {
                popularWins++;
            }
        }

        assertThat(popularWins).isGreaterThan(190);
    }

    @Test
    void betaDrawsFollowTheEvidence() {
        double mostlyClicked = 0;
        double neverSeen = 0;
        for (int draw = 0; draw < 2_000; draw++) {
            mostlyClicked += service.beta(1 + 80, 1 + 20);
            neverSeen += service.beta(1, 1);
        }

        assertThat(mostlyClicked / 2_000).isBetween(0.77, 0.82);
        assertThat(neverSeen / 2_000).as("a new posting is as likely to be good as bad").isBetween(0.47, 0.53);
    }

    // ---- helpers

    private static List<Posting> ranked(int count, double best, double step) {
        List<Posting> ranked = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            ranked.add(new Posting(UUID.randomUUID(), best - step * index));
        }
        return ranked;
    }

    /** Each value is {impressions_7d, clicks_7d, impressions_total, clicks_total}. */
    private void stats(Map<UUID, int[]> values) {
        List<RecommendationExposureStats> rows = new ArrayList<>();
        values.forEach((id, value) -> rows.add(RecommendationExposureStats.builder()
                .id(new RecommendationExposureStatsId(TYPE, id))
                .impressions7d(value[0]).clicks7d(value[1]).impressionsTotal(value[2]).clicksTotal(value[3]).build()));
        when(statsRepository.findAllOf(any(), anyCollection())).thenReturn(rows);
    }
}
