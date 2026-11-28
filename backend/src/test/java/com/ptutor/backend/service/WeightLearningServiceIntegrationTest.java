package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.recommendation.WeightModel;
import com.ptutor.backend.entity.RecommendationLearnedWeights;
import com.ptutor.backend.entity.enums.WeightLearningMode;
import com.ptutor.backend.repository.RecommendationLearnedWeightsRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The nightly update of the learned weights against PostgreSQL (design 12, 12.7). Data is dated early 2020, away
 * from anything else in the database; everything is rolled back and the shared weight provider loads again after.
 */
@SpringBootTest
@Transactional
class WeightLearningServiceIntegrationTest {

    /** Willingness data up to now; the last 7 days (from 23 February) are held out. */
    private static final LocalDateTime NOW = LocalDateTime.of(2020, 3, 1, 0, 0);
    private static final LocalDateTime TRAINING = LocalDateTime.of(2020, 1, 15, 9, 0);
    private static final LocalDateTime HELD_OUT = LocalDateTime.of(2020, 2, 26, 9, 0);

    @Autowired WeightLearningService learningService;
    @Autowired RecommendationLearnedWeightsRepository weightsRepository;
    @Autowired WeightProvider weightProvider;
    @Autowired RecommendationSettings settings;
    @Autowired JdbcTemplate jdbc;

    private UUID subjectId;
    private UUID gradeId;
    private UUID studentId;
    private UUID tutorId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM recommendation_learned_weights");
        ReflectionTestUtils.setField(settings, "learningPriorStrength", 5.0);
        subjectId = insert("INSERT INTO subjects (id, name, status) VALUES (?, ?, 'ACTIVE')", "Toán-" + UUID.randomUUID());
        gradeId = insert("INSERT INTO grades (id, name, status, level) VALUES (?, ?, 'ACTIVE', 9)",
                "Lớp 9-" + UUID.randomUUID());
        studentId = insert("INSERT INTO students (id, user_id) VALUES (?, ?)", user());
        tutorId = insert("INSERT INTO tutors (id, user_id) VALUES (?, ?)", user());
    }

    @AfterEach
    void restoreSettings() {
        ReflectionTestUtils.setField(settings, "learningPriorStrength", 50.0);
        ReflectionTestUtils.setField(settings, "learningMinSamplesB", 300);
        ReflectionTestUtils.setField(settings, "learningMinSamplesA", 2000);
        ReflectionTestUtils.setField(settings, "learningMinSearchesA", 300);
        ReflectionTestUtils.setField(settings, "learningModeBS2T", WeightLearningMode.SHADOW);
        ReflectionTestUtils.setField(settings, "learningModeAS2T", WeightLearningMode.SHADOW);
        ReflectionTestUtils.setField(settings, "learningObservationDumpDir", "");
    }

    /** The rows were rolled back: serve what the database really holds again. */
    @AfterTransaction
    void reloadTheSharedProvider() {
        weightProvider.reload();
    }

    @Test
    void insertsANewVersionFromTheNewDecisionsButKeepsThePriorWithoutEnoughData() {
        tutorsAcceptStudentsWithAHighHistory(TRAINING, 20);
        tutorsAcceptStudentsWithAHighHistory(HELD_OUT, 5);

        List<WeightLearningService.Outcome> outcomes = learningService.learnAll(NOW);

        assertThat(outcomes).filteredOn(outcome -> outcome.model() == WeightModel.B_S2T).singleElement()
                .satisfies(outcome -> {
                    assertThat(outcome.version()).isEqualTo(1);
                    assertThat(outcome.newSamples()).isEqualTo(40);
                    assertThat(outcome.activated()).isFalse();
                });
        RecommendationLearnedWeights version = weightsRepository.findFirstByModelKeyOrderByVersionDesc(WeightModel.B_S2T)
                .orElseThrow();
        assertThat(version.getSampleCount()).isEqualTo(40);
        assertThat(version.getTrainedUntil()).isEqualTo(NOW.minusDays(7));
        assertThat(version.getActive()).isFalse();
        assertThat(version.getEvaluation().path("n").asInt()).isEqualTo(10);
        assertThat(version.getStatsA().size()).isEqualTo(5);
        // Acceptance follows the tutor's history: its weight grows, by at most one step of 0.05 before normalising.
        double hist = version.getWeights().path("hist").asDouble();
        // Every weight moves at most 0.05, so the other three keep at least 3 × (1/6 - 0.05) = 0.35.
        assertThat(hist).isGreaterThan(0.5).isLessThanOrEqualTo(0.55 / 0.90 + 1e-9);
        // Nothing was activated: the provider still serves the prior.
        assertThat(weightProvider.weights(WeightModel.B_S2T)).isEqualTo(weightProvider.prior(WeightModel.B_S2T));
    }

    @Test
    void activatesAVersionThatHasEnoughDataAndIsNotWorseThanThePriorAndTheProviderLoadsIt() {
        ReflectionTestUtils.setField(settings, "learningMinSamplesB", 10);
        tutorsAcceptStudentsWithAHighHistory(TRAINING, 20);
        tutorsAcceptStudentsWithAHighHistory(HELD_OUT, 5);

        assertThat(learningService.learnAll(NOW)).filteredOn(outcome -> outcome.model() == WeightModel.B_S2T)
                .singleElement().satisfies(outcome -> assertThat(outcome.activated()).isTrue());

        RecommendationLearnedWeights active = weightsRepository.findByModelKeyAndActiveTrue(WeightModel.B_S2T)
                .orElseThrow();
        assertThat(active.getEvaluation().path("metric").asString()).isEqualTo("auc");
        assertThat(active.getEvaluation().path("learned").asDouble())
                .isGreaterThanOrEqualTo(active.getEvaluation().path("prior").asDouble()
                        - settings.getLearningActivationToleranceAuc());
        assertThat(active.getEvaluation().path("loglossLearned").isNumber()).as("log-loss kept for reference").isTrue();
        // SHADOW: learned and activated, but users are still served the prior.
        assertThat(weightProvider.weights(WeightModel.B_S2T)).isEqualTo(weightProvider.prior(WeightModel.B_S2T));
        ReflectionTestUtils.setField(settings, "learningModeBS2T", WeightLearningMode.ACTIVE);
        assertThat(weightProvider.weights(WeightModel.B_S2T).get("hist"))
                .isEqualTo(active.getWeights().path("hist").asDouble());
    }

    @Test
    void doesNotActivateAVersionThatIsWorseThanThePriorOnTheHeldOutData() {
        ReflectionTestUtils.setField(settings, "learningMinSamplesB", 10);
        tutorsAcceptStudentsWithAHighHistory(TRAINING, 20);
        // The most recent decisions favour the fit over the history, on two kinds of request that the prior and the
        // learned weights (history up) order differently: the prior puts (0.5; 0.5, 0.5, 1) above (0.83; 0.5, 0.5, 0),
        // the learned weights put it below once the history weight exceeds ~0.5025.
        for (int i = 0; i < 5; i++) {
            decision(0.83, 0.0, false, HELD_OUT.plusMinutes(i));
            decision(0.5, 1.0, true, HELD_OUT.plusMinutes(10 + i));
        }

        assertThat(learningService.learnAll(NOW)).filteredOn(outcome -> outcome.model() == WeightModel.B_S2T)
                .singleElement().satisfies(outcome -> assertThat(outcome.activated()).isFalse());
        RecommendationLearnedWeights version = weightsRepository.findFirstByModelKeyOrderByVersionDesc(WeightModel.B_S2T)
                .orElseThrow();
        assertThat(version.getWeights().path("hist").asDouble()).as("precondition: the history weight grew")
                .isGreaterThan(0.5025);
        // Worse by more than the tolerance (12.7): the prior orders every pair right, the learned weights none.
        assertThat(version.getEvaluation().path("metric").asString()).isEqualTo("auc");
        assertThat(version.getEvaluation().path("prior").asDouble()).isEqualTo(1.0);
        assertThat(version.getEvaluation().path("learned").asDouble())
                .isLessThan(version.getEvaluation().path("prior").asDouble()
                        - settings.getLearningActivationToleranceAuc());
        assertThat(weightsRepository.findByModelKeyAndActiveTrue(WeightModel.B_S2T)).isEmpty();
    }

    @Test
    void theNextNightContinuesFromTheLatestVersionAndAddsOnlyNewData() {
        tutorsAcceptStudentsWithAHighHistory(TRAINING, 20);
        learningService.learnAll(NOW);
        double priorScale = weightsRepository.findFirstByModelKeyOrderByVersionDesc(WeightModel.B_S2T).orElseThrow()
                .getEvaluation().path("priorScale").asDouble();
        assertThat(priorScale).as("β measured on the first batch").isGreaterThanOrEqualTo(0.01);

        // Nothing new: no new version.
        assertThat(learningService.learnAll(NOW)).filteredOn(outcome -> outcome.model() == WeightModel.B_S2T)
                .singleElement().satisfies(outcome -> assertThat(outcome.version()).isNull());

        // A week later the week that was held out is learned, with the decisions made in it.
        tutorsAcceptStudentsWithAHighHistory(NOW.minusDays(3), 3);
        learningService.learnAll(NOW.plusDays(8));
        RecommendationLearnedWeights second = weightsRepository.findFirstByModelKeyOrderByVersionDesc(WeightModel.B_S2T)
                .orElseThrow();
        assertThat(second.getVersion()).isEqualTo(2);
        assertThat(second.getSampleCount()).isEqualTo(46);
        assertThat(second.getEvaluation().path("priorScale").asDouble()).as("β kept by later versions")
                .isEqualTo(priorScale);
    }

    @Test
    void learnsTheSuitabilityFromSearchesAndSkipsModelsThatAreOff() {
        ReflectionTestUtils.setField(settings, "learningModeBS2T", WeightLearningMode.OFF);
        List<UUID> postings = List.of(posting(), posting(), posting());
        UUID log = searchLog(TRAINING, postings);
        jdbc.update("""
                INSERT INTO recommendation_click_logs (id, recommendation_log_id, clicked_student_id, rank, action,
                                                       candidate_teaching_request_id, created_at, updated_at)
                VALUES (?, ?, ?, 1, 'FAVORITED', ?, ?, ?)
                """, UUID.randomUUID(), log, studentId, postings.get(0), ts(TRAINING), ts(TRAINING));
        tutorsAcceptStudentsWithAHighHistory(TRAINING, 5);

        List<WeightLearningService.Outcome> outcomes = learningService.learnAll(NOW);

        assertThat(outcomes).extracting(WeightLearningService.Outcome::model).doesNotContain(WeightModel.B_S2T);
        RecommendationLearnedWeights suitability = weightsRepository
                .findFirstByModelKeyOrderByVersionDesc(WeightModel.A_S2T).orElseThrow();
        // Rank 1 was favourited: ranks 1 to 3 were read.
        assertThat(suitability.getSampleCount()).isEqualTo(3);
        assertThat(suitability.getEvaluation().path("searches").asInt()).isEqualTo(1);
        assertThat(suitability.getFeatureNames().size()).isEqualTo(8);
        assertThat(suitability.getActive()).isFalse();
        assertThat(weightsRepository.findFirstByModelKeyOrderByVersionDesc(WeightModel.B_S2T)).isEmpty();
    }

    /** The end-to-end check of design 13, 13.7 (step 43a) compares these files with what the simulation did. */
    @Test
    void writesTheObservationsItUsedWhenADumpFolderIsSet(@TempDir Path folder) throws Exception {
        ReflectionTestUtils.setField(settings, "learningModeBS2T", WeightLearningMode.OFF);
        ReflectionTestUtils.setField(settings, "learningObservationDumpDir", folder.toString());
        List<UUID> postings = List.of(posting(), posting(), posting());
        UUID log = searchLog(TRAINING, postings);
        jdbc.update("""
                INSERT INTO recommendation_click_logs (id, recommendation_log_id, clicked_student_id, rank, action,
                                                       candidate_teaching_request_id, created_at, updated_at)
                VALUES (?, ?, ?, 1, 'FAVORITED', ?, ?, ?)
                """, UUID.randomUUID(), log, studentId, postings.get(0), ts(TRAINING), ts(TRAINING));

        learningService.learnAll(NOW);

        JsonNode dump = JsonMapper.builder().build().readTree(Files.readString(folder.resolve("A_S2T-v1.json")));
        RecommendationLearnedWeights stored = weightsRepository
                .findFirstByModelKeyOrderByVersionDesc(WeightModel.A_S2T).orElseThrow();
        JsonNode candidates = dump.path("training").path("searches").path(0).path("candidates");
        assertThat(dump.path("version").asInt()).isEqualTo(1);
        assertThat(dump.path("training").path("searches").size()).isEqualTo(1);
        assertThat(dump.path("training").path("searches").path(0).path("logId").asString()).isEqualTo(log.toString());
        assertThat(candidates.size()).isEqualTo(3);
        assertThat(candidates.path(0).path("candidateId").asString()).isEqualTo(postings.get(0).toString());
        assertThat(candidates.path(0).path("reward").asDouble()).isEqualTo(settings.getLearningRewardFavorite());
        assertThat(candidates.path(1).path("reward").asDouble()).isZero();
        // sem is logged; the other features are not, so they are neutral.
        assertThat(candidates.path(0).path("features").path(0).asDouble()).isEqualTo(0.9);
        assertThat(candidates.path(0).path("features").path(1).asDouble()).isEqualTo(settings.getConfidencePrior());
        assertThat(dump.path("statsC")).isEqualTo(stored.getStatsC());
        assertThat(dump.path("weights").path(0).asDouble())
                .isEqualTo(stored.getWeights().path(WeightModel.A_S2T.features().get(0)).asDouble());
    }

    @Test
    void writesNothingWithoutADumpFolder(@TempDir Path folder) {
        ReflectionTestUtils.setField(settings, "learningModeBS2T", WeightLearningMode.OFF);
        UUID log = searchLog(TRAINING, List.of(posting()));
        jdbc.update("""
                INSERT INTO recommendation_click_logs (id, recommendation_log_id, clicked_student_id, rank, action,
                                                       created_at, updated_at)
                VALUES (?, ?, ?, 1, 'CLICKED', ?, ?)
                """, UUID.randomUUID(), log, studentId, ts(TRAINING), ts(TRAINING));

        learningService.learnAll(NOW);

        assertThat(settings.getLearningObservationDumpDir()).isEmpty();
        assertThat(folder.toFile().list()).isEmpty();
    }

    /** A deleted search log is not learned from (step 43a removes the browsing history of the seed this way). */
    @Test
    void aDeletedSearchLogIsNotLearnedFrom() {
        ReflectionTestUtils.setField(settings, "learningModeBS2T", WeightLearningMode.OFF);
        List<UUID> postings = List.of(posting(), posting());
        UUID log = searchLog(TRAINING, postings);
        jdbc.update("""
                INSERT INTO recommendation_click_logs (id, recommendation_log_id, clicked_student_id, rank, action,
                                                       candidate_teaching_request_id, created_at, updated_at)
                VALUES (?, ?, ?, 1, 'FAVORITED', ?, ?, ?)
                """, UUID.randomUUID(), log, studentId, postings.get(0), ts(TRAINING), ts(TRAINING));
        jdbc.update("UPDATE recommendation_logs SET deleted_at = ? WHERE id = ?", ts(TRAINING), log);

        assertThat(learningService.learnAll(NOW)).filteredOn(outcome -> outcome.model() == WeightModel.A_S2T)
                .singleElement().satisfies(outcome -> assertThat(outcome.version()).isNull());
        assertThat(weightsRepository.findFirstByModelKeyOrderByVersionDesc(WeightModel.A_S2T)).isEmpty();
    }

    // ---------------------------------------------------------------- helpers

    /** {@code count} accepted applications of students with a strong history and as many rejected weak ones. */
    private void tutorsAcceptStudentsWithAHighHistory(LocalDateTime at, int count) {
        for (int i = 0; i < count; i++) {
            decision(0.9, true, at.plusMinutes(i));
            decision(0.1, false, at.plusMinutes(count + i));
        }
    }

    private void decision(double hist, boolean accepted, LocalDateTime at) {
        decision(hist, 0.5, accepted, at);
    }

    private void decision(double hist, double fitPrice, boolean accepted, LocalDateTime at) {
        UUID request = insert("""
                INSERT INTO student_tutor_requests (id, student_id, grade_id, teaching_request_id, learning_mode, status,
                                                    created_at, updated_at, responded_at)
                VALUES (?, ?, ?, ?, 'ONLINE', ?, ?, ?, ?)
                """, studentId, gradeId, posting(), accepted ? "ACCEPTED" : "REJECTED", ts(at.minusDays(2)), ts(at),
                ts(at));
        jdbc.update("""
                INSERT INTO recommendation_decision_snapshots
                    (model_key, sample_kind, request_type, request_id, features, created_at)
                VALUES ('B_S2T', 'DECISION', 'STUDENT_TUTOR_REQUEST', ?, CAST(? AS jsonb), ?)
                """, request, "{\"hist\":" + hist + ",\"fitSegment\":0.5,\"fitSched\":0.5,\"fitPrice\":" + fitPrice + "}",
                ts(at.minusDays(2)));
    }

    private UUID searchLog(LocalDateTime at, List<UUID> postings) {
        StringBuilder candidates = new StringBuilder("[");
        for (int i = 0; i < postings.size(); i++) {
            candidates.append(i == 0 ? "" : ",").append("{\"candidateId\":\"").append(postings.get(i))
                    .append("\",\"rank\":").append(i + 1).append(",\"features\":{\"sem\":").append(0.9 - 0.2 * i)
                    .append("}}");
        }
        return insert("""
                INSERT INTO recommendation_logs (id, searcher_student_id, direction, returned_candidate_ids, created_at,
                                                 updated_at)
                VALUES (?, ?, 'STUDENT_TO_TUTOR', CAST(? AS jsonb), ?, ?)
                """, studentId, candidates.append(']').toString(), ts(at), ts(at));
    }

    private UUID posting() {
        return insert("""
                INSERT INTO teaching_requests (id, tutor_id, subject_id, teaching_mode, status)
                VALUES (?, ?, ?, 'ONLINE', 'OPEN')
                """, tutorId, subjectId);
    }

    private UUID user() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, citizen_id, email, password, status) VALUES (?, ?, ?, 'x', 'ACTIVE')",
                id, "cid-" + id, id + "@example.com");
        return id;
    }

    private UUID insert(String sql, Object... args) {
        UUID id = UUID.randomUUID();
        Object[] all = new Object[args.length + 1];
        all[0] = id;
        System.arraycopy(args, 0, all, 1, args.length);
        jdbc.update(sql, all);
        return id;
    }

    private static Timestamp ts(LocalDateTime value) {
        return Timestamp.valueOf(value);
    }
}
