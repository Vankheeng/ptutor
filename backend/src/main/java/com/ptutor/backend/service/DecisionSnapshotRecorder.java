package com.ptutor.backend.service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;

import com.ptutor.backend.dto.recommendation.FeatureVector;
import com.ptutor.backend.dto.recommendation.ModePreference;
import com.ptutor.backend.dto.recommendation.QueryContext;
import com.ptutor.backend.dto.recommendation.QueryContext.WeightedTag;
import com.ptutor.backend.dto.recommendation.StudyingCandidate;
import com.ptutor.backend.dto.recommendation.SupplyContext;
import com.ptutor.backend.dto.recommendation.TeachingCandidate;
import com.ptutor.backend.dto.recommendation.WeightModel;
import com.ptutor.backend.dto.recommendation.Willingness;
import com.ptutor.backend.dto.request.StudentRecommendationRequest;
import com.ptutor.backend.dto.request.TutorRecommendationRequest;
import com.ptutor.backend.entity.Grade;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.StudyingRequest;
import com.ptutor.backend.entity.TeachingRequest;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.enums.DecisionRequestType;
import com.ptutor.backend.entity.enums.DecisionSampleKind;
import com.ptutor.backend.entity.enums.LearningMode;
import com.ptutor.backend.entity.enums.TeachingMode;
import com.ptutor.backend.repository.CandidateRepository;
import com.ptutor.backend.repository.CandidateRepository.Hit;
import com.ptutor.backend.repository.StudyingCandidateRepository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Snapshots of the willingness features of a pair when an application or proposal is created (design 12,
 * 12.6): what b and b' were made of at that moment, to learn their weights from the answer later. Every
 * application gives two samples: DECISION for the model of the side that has to answer, INITIATIVE (a positive
 * sample) for the model of the side that reached out.
 *
 * <p>The features are computed before the application is saved ({@link #prepareStudentApplication},
 * {@link #prepareTutorProposal}), so the application does not count in its own congestion, and written after it
 * was saved ({@link #record}). Both run inside the caller's transaction, each within a savepoint: a failure is
 * logged and rolled back to the savepoint, and never stops the application from being created.
 */
@Component
@RequiredArgsConstructor
public class DecisionSnapshotRecorder {

    private static final Logger log = LoggerFactory.getLogger(DecisionSnapshotRecorder.class);

    /** One sample: the features of one willingness model for the pair. */
    record Sample(WeightModel model, DecisionSampleKind kind, ObjectNode features) {
    }

    /** The samples of an application that is about to be saved. */
    public record Prepared(DecisionRequestType requestType, List<Sample> samples) {
    }

    private final QueryContextBuilder queryContextBuilder;
    private final SupplyContextBuilder supplyContextBuilder;
    private final CandidateRepository candidateRepository;
    private final StudyingCandidateRepository studyingCandidateRepository;
    private final FeatureCalculator featureCalculator;
    private final WillingnessCalculator willingnessCalculator;
    private final TutorSideRanker tutorSideRanker;
    private final RecommendationSettings settings;
    private final NamedParameterJdbcTemplate jdbc;
    private final DataSource dataSource;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    /**
     * A student applies to a teaching posting. DECISION for {@code B_S2T}: will the tutor take this student?
     * INITIATIVE for {@code B_T2S}: the student is willing to learn with this tutor.
     *
     * @return null when the snapshot could not be computed
     */
    public Prepared prepareStudentApplication(
            Student student, TeachingRequest posting, Grade grade, BigDecimal price, LearningMode mode) {
        return insideSavepoint("prepare the snapshots of a student application", () -> {
            // The student's side as a search with the application's grade, price and mode; the rest from the profile.
            QueryContext studentSide = queryContextBuilder.forStudent(student, new TutorRecommendationRequest(
                    new TutorRecommendationRequest.Filters(
                            posting.getSubject() == null ? null : posting.getSubject().getId(),
                            grade == null ? null : grade.getId(), modeOf(mode), null,
                            price == null ? null : new TutorRecommendationRequest.Price(null, price),
                            null, null, null, null),
                    null, null, null));
            List<Sample> samples = new ArrayList<>();
            List<TeachingCandidate> loaded = candidateRepository.load(student.getId(),
                    List.of(new Hit(posting.getId(), posting.getCreatedAt(), null)),
                    settings.getWillingnessHistoryDays());
            if (!loaded.isEmpty()) {
                TeachingCandidate candidate = loaded.get(0);
                samples.add(studentToTutor(DecisionSampleKind.DECISION, studentSide, candidate));
            }

            // The tutor's side as their posting describes it; the student as the candidate they would see.
            SupplyContext tutorSide = supplyContextBuilder.forTutor(posting.getTutor(),
                    new StudentRecommendationRequest(null, posting.getId()));
            samples.add(tutorToStudent(DecisionSampleKind.INITIATIVE, tutorSide,
                    studentAsCandidate(student, studentSide, mode)));
            return new Prepared(DecisionRequestType.STUDENT_TUTOR_REQUEST, samples);
        });
    }

    /**
     * A tutor proposes to a studying posting. DECISION for {@code B_T2S}: will the student accept this tutor?
     * INITIATIVE for {@code B_S2T}: the tutor is willing to teach this student.
     *
     * @return null when the snapshot could not be computed
     */
    public Prepared prepareTutorProposal(
            Tutor tutor, StudyingRequest posting, Grade grade, BigDecimal price, TeachingMode mode) {
        return insideSavepoint("prepare the snapshots of a tutor proposal", () -> {
            // The tutor's side as a search with the proposal's grade, price and mode; the rest from the profile.
            SupplyContext tutorSide = supplyContextBuilder.forTutor(tutor, new StudentRecommendationRequest(
                    new StudentRecommendationRequest.Filters(
                            posting.getSubject() == null ? null : posting.getSubject().getId(),
                            grade == null ? null : List.of(grade.getId()), modeOf(mode), null, price, null, null),
                    null));
            List<Sample> samples = new ArrayList<>();
            List<StudyingCandidate> loaded = studyingCandidateRepository.load(tutor.getId(),
                    List.of(new Hit(posting.getId(), posting.getCreatedAt(), null)),
                    settings.getWillingnessHistoryDays());
            if (!loaded.isEmpty()) {
                samples.add(tutorToStudent(DecisionSampleKind.DECISION, tutorSide, loaded.get(0)));
            }

            // The student's side as their posting describes it; the tutor as the candidate they would see.
            QueryContext studentSide = queryContextBuilder.forStudent(posting.getStudent(),
                    new TutorRecommendationRequest(null, null, null, posting.getId()));
            samples.add(studentToTutor(DecisionSampleKind.INITIATIVE, studentSide,
                    tutorAsCandidate(tutor, tutorSide, mode)));
            return new Prepared(DecisionRequestType.TUTOR_STUDENT_REQUEST, samples);
        });
    }

    /** Writes the prepared samples for the saved application. Does nothing when nothing was prepared. */
    public void record(Prepared prepared, UUID requestId) {
        if (prepared == null || prepared.samples().isEmpty()) {
            return;
        }
        insideSavepoint("record the snapshots of " + prepared.requestType() + " " + requestId, () -> {
            for (Sample sample : prepared.samples()) {
                jdbc.update("""
                        INSERT INTO recommendation_decision_snapshots
                            (model_key, sample_kind, request_type, request_id, features)
                        VALUES (:model, :kind, :requestType, :requestId, CAST(:features AS jsonb))
                        """, new MapSqlParameterSource()
                                .addValue("model", sample.model().name())
                                .addValue("kind", sample.kind().name())
                                .addValue("requestType", prepared.requestType().name())
                                .addValue("requestId", requestId)
                                .addValue("features", objectMapper.writeValueAsString(sample.features())));
            }
            return Boolean.TRUE;
        });
    }

    /** b of the pair, from the student's search side: its parts in the order of {@link WeightModel#B_S2T}. */
    private Sample studentToTutor(DecisionSampleKind kind, QueryContext studentSide, TeachingCandidate candidate) {
        double platformRate = candidateRepository.platformAcceptanceRate(settings.getWillingnessHistoryDays(),
                settings.getWillingnessMinDecisions(), settings.getWillingnessDefaultRate());
        FeatureVector features = featureCalculator.calculate(studentSide, candidate);
        Willingness willingness = willingnessCalculator.calculate(studentSide, candidate, features, platformRate);
        return new Sample(WeightModel.B_S2T, kind, featuresJson(WeightModel.B_S2T, willingness,
                candidate.pendingApplications(), platformRate));
    }

    /** b' of the pair, from the tutor's search side: its parts in the order of {@link WeightModel#B_T2S}. */
    private Sample tutorToStudent(DecisionSampleKind kind, SupplyContext tutorSide, StudyingCandidate candidate) {
        double platformRate = studyingCandidateRepository.platformAcceptanceRate(
                settings.getWillingnessHistoryDays(), settings.getWillingnessMinDecisions(),
                settings.getWillingnessDefaultRate());
        Willingness willingness = tutorSideRanker.willingness(tutorSide, candidate, platformRate);
        return new Sample(WeightModel.B_T2S, kind, featuresJson(WeightModel.B_T2S, willingness,
                candidate.pendingProposals(), platformRate));
    }

    /**
     * {"hist", fit parts by name, "cap", "pending", "reachedOut", "p0"}: what b or b' was made of. The fit parts
     * are the values b used, with missing data already replaced.
     */
    private ObjectNode featuresJson(WeightModel model, Willingness willingness, int pending, double platformRate) {
        ObjectNode json = objectMapper.createObjectNode();
        json.put("hist", willingness.history());
        List<String> names = model.features().subList(1, model.features().size());
        for (int i = 0; i < names.size(); i++) {
            json.put(names.get(i), willingness.fitParts().get(i));
        }
        json.put("cap", willingness.capacity());
        json.put("pending", pending);
        json.put("reachedOut", willingness.invited());
        json.put("p0", platformRate);
        return json;
    }

    /**
     * The student as a studying candidate when they have no posting for it: their search side (from the
     * application and the profile) and their past answers to tutors. They reached out, so they count as applied.
     */
    private StudyingCandidate studentAsCandidate(Student student, QueryContext studentSide, LearningMode mode) {
        List<String> styles = studentSide.styleTags().stream().map(WeightedTag::code).distinct().toList();
        return new StudyingCandidate(null, student.getId(), student.getUser() == null ? null : student.getUser().getId(),
                null, null, studentSide.minPrice(), studentSide.maxPrice(), mode, null, null, null, null,
                studentSide.availabilities(), studentSide.needTags(), Set.copyOf(styles),
                studentSide.levelTagCode(), false, 1, 0,
                studyingCandidateRepository.studentDecisions(List.of(student.getId()),
                        settings.getWillingnessHistoryDays()).getOrDefault(student.getId(), List.of()),
                true);
    }

    /**
     * The tutor as a teaching candidate when they have no posting for it: their search side (from the proposal
     * and the profile) and their past answers to students. They reached out, so they count as inviting.
     */
    private TeachingCandidate tutorAsCandidate(Tutor tutor, SupplyContext tutorSide, TeachingMode mode) {
        return new TeachingCandidate(null, tutor.getId(), tutor.getUser() == null ? null : tutor.getUser().getId(),
                null, tutorSide.expectedPrice(), mode, null, null, tutor.getRecommendationScore(), null, null, null,
                null, List.of(), tutorSide.availabilities(), tutorSide.strengthTags(), tutorSide.offeredStyleCodes(),
                tutorSide.targetSegmentCodes(), false, 1, 0,
                candidateRepository.tutorDecisions(List.of(tutor.getId()), settings.getWillingnessHistoryDays())
                        .getOrDefault(tutor.getId(), List.of()),
                true);
    }

    private static ModePreference modeOf(Enum<?> mode) {
        return mode == null ? null : ModePreference.valueOf(mode.name());
    }

    /**
     * Runs {@code work} so that a failure, even a failed SQL statement, leaves the caller's transaction usable:
     * pending changes are flushed first, then the work runs after a savepoint and is rolled back to it on error.
     */
    private <T> T insideSavepoint(String what, Supplier<T> work) {
        Connection connection = null;
        Savepoint savepoint = null;
        try {
            entityManager.flush();
            connection = DataSourceUtils.getConnection(dataSource);
            savepoint = connection.getAutoCommit() ? null : connection.setSavepoint();
            T result = work.get();
            if (savepoint != null) {
                connection.releaseSavepoint(savepoint);
            }
            return result;
        } catch (Exception exception) {
            if (savepoint != null) {
                try {
                    connection.rollback(savepoint);
                } catch (Exception rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
            }
            log.warn("Unable to {}; the application is created without it", what, exception);
            return null;
        } finally {
            if (connection != null) {
                DataSourceUtils.releaseConnection(connection, dataSource);
            }
        }
    }
}
