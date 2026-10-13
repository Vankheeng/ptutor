package com.ptutor.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ptutor.backend.dto.recommendation.MatchPosition;
import com.ptutor.backend.dto.request.StudentRecommendationRequest;
import com.ptutor.backend.dto.request.TutorRecommendationRequest;
import com.ptutor.backend.entity.Notification;
import com.ptutor.backend.entity.RecommendationMatchAlert;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.NotificationEventType;
import com.ptutor.backend.entity.enums.NotificationReferenceType;
import com.ptutor.backend.entity.enums.RecommendationCandidateType;
import com.ptutor.backend.entity.enums.RecommendationEntityType;
import com.ptutor.backend.entity.enums.RecommendationMatchSourceType;
import com.ptutor.backend.event.NotificationDomainEvent;
import com.ptutor.backend.repository.NotificationRepository;
import com.ptutor.backend.repository.RecommendationMatchAlertRepository;

import jakarta.persistence.EntityManager;

/**
 * C7 (design 07, 7.3): when a posting appears or changes, look at everyone on the other side who is
 * waiting for something like it - through an OPEN posting of their own or a saved search with
 * notifications on - and tell those for whom it would be one of the very best results. Strict on
 * purpose: one notification per person per day at most, and never twice about the same posting.
 */
@Service
public class MatchAlertService {

    private static final Logger log = LoggerFactory.getLogger(MatchAlertService.class);

    private static final String POSTING = "SELECT subject_id, status FROM %s WHERE id = ? AND deleted_at IS NULL";

    /** Students waiting for a tutor of this subject: their OPEN studying postings and their saved searches. */
    private static final String STUDENT_SOURCES = """
            SELECT 'STUDYING_REQUEST' AS source_type, sr.id AS source_id, s.user_id AS user_id
            FROM studying_requests sr
            JOIN students s ON s.id = sr.student_id AND s.deleted_at IS NULL
            JOIN users u ON u.id = s.user_id AND u.status = 'ACTIVE'
            WHERE sr.deleted_at IS NULL AND sr.status = 'OPEN' AND sr.subject_id = ?
            UNION ALL
            SELECT 'SAVED_SEARCH', ss.id, ss.user_id
            FROM recommendation_saved_searches ss
            JOIN users u ON u.id = ss.user_id AND u.status = 'ACTIVE'
            WHERE ss.deleted_at IS NULL AND ss.notify_enabled AND ss.direction = 'STUDENT_TO_TUTOR'
              AND ss.query_context -> 'filters' ->> 'subjectId' = ?
            """;

    /** Verified tutors waiting for a student of this subject: their OPEN teaching postings and saved searches. */
    private static final String TUTOR_SOURCES = """
            SELECT 'TEACHING_REQUEST' AS source_type, tr.id AS source_id, t.user_id AS user_id
            FROM teaching_requests tr
            JOIN tutors t ON t.id = tr.tutor_id AND t.deleted_at IS NULL AND t.profile_status = 'VERIFIED'
            JOIN users u ON u.id = t.user_id AND u.status = 'ACTIVE'
            WHERE tr.deleted_at IS NULL AND tr.status = 'OPEN' AND tr.subject_id = ?
            UNION ALL
            SELECT 'SAVED_SEARCH', ss.id, ss.user_id
            FROM recommendation_saved_searches ss
            JOIN users u ON u.id = ss.user_id AND u.status = 'ACTIVE'
            WHERE ss.deleted_at IS NULL AND ss.notify_enabled AND ss.direction = 'TUTOR_TO_STUDENT'
              AND ss.query_context -> 'filters' ->> 'subjectId' = ?
            """;

    private final JdbcTemplate jdbc;
    private final RecommendationService recommendationService;
    private final StudentRecommendationService studentRecommendationService;
    private final SavedSearchService savedSearchService;
    private final RecommendationMatchAlertRepository alertRepository;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final EntityManager entityManager;
    private final RecommendationSettings settings;
    private final TransactionTemplate transaction;

    public MatchAlertService(
            JdbcTemplate jdbc,
            RecommendationService recommendationService,
            StudentRecommendationService studentRecommendationService,
            SavedSearchService savedSearchService,
            RecommendationMatchAlertRepository alertRepository,
            NotificationService notificationService,
            NotificationRepository notificationRepository,
            EntityManager entityManager,
            RecommendationSettings settings,
            PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.recommendationService = recommendationService;
        this.studentRecommendationService = studentRecommendationService;
        this.savedSearchService = savedSearchService;
        this.alertRepository = alertRepository;
        this.notificationService = notificationService;
        this.notificationRepository = notificationRepository;
        this.entityManager = entityManager;
        this.settings = settings;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    private record Source(RecommendationMatchSourceType type, UUID id, UUID userId) {
    }

    /**
     * @param postingType TEACHING_REQUEST or STUDYING_REQUEST
     * @return how many people were notified
     */
    public int process(RecommendationEntityType postingType, UUID postingId) {
        boolean teaching = postingType == RecommendationEntityType.TEACHING_REQUEST;
        if (!settings.isAlertEnabled() || (!teaching && postingType != RecommendationEntityType.STUDYING_REQUEST)) {
            return 0;
        }
        List<Map<String, Object>> posting = jdbc.queryForList(
                POSTING.formatted(teaching ? "teaching_requests" : "studying_requests"), postingId);
        if (posting.isEmpty() || posting.get(0).get("subject_id") == null || !"OPEN".equals(posting.get(0).get("status"))) {
            return 0;
        }
        UUID subjectId = (UUID) posting.get(0).get("subject_id");
        List<Source> sources = new ArrayList<>();
        jdbc.query(teaching ? STUDENT_SOURCES : TUTOR_SOURCES, row -> {
            sources.add(new Source(RecommendationMatchSourceType.valueOf(row.getString("source_type")),
                    row.getObject("source_id", UUID.class), row.getObject("user_id", UUID.class)));
        }, subjectId, subjectId.toString());

        RecommendationCandidateType candidateType = teaching
                ? RecommendationCandidateType.TEACHING_REQUEST : RecommendationCandidateType.STUDYING_REQUEST;
        int notified = 0;
        for (Source source : sources) {
            try {
                // Each source on its own: a saved search that can no longer be run must not stop the others.
                Boolean sent = transaction.execute(status -> notifyIfTopMatch(source, candidateType, postingId));
                notified += Boolean.TRUE.equals(sent) ? 1 : 0;
            } catch (RuntimeException exception) {
                log.debug("Match alert skipped for {} {}: {}", source.type(), source.id(), exception.toString());
            }
        }
        return notified;
    }

    private boolean notifyIfTopMatch(Source source, RecommendationCandidateType candidateType, UUID postingId) {
        LocalDateTime now = LocalDateTime.now();
        if (alertRepository.existsBySourceTypeAndSourceIdAndCandidateTypeAndCandidateId(
                        source.type(), source.id(), candidateType, postingId)
                || alertRepository.existsByRecipientUser_IdAndCreatedAtAfter(
                        source.userId(), now.minusHours(settings.getAlertCooldownHours()))) {
            return false;
        }
        boolean teaching = candidateType == RecommendationCandidateType.TEACHING_REQUEST;
        Optional<MatchPosition> found;
        if (teaching) {
            TutorRecommendationRequest request = source.type() == RecommendationMatchSourceType.SAVED_SEARCH
                    ? savedSearchService.studentRequest(source.userId(), source.id())
                    : new TutorRecommendationRequest(null, null, null, source.id());
            found = recommendationService.matchPosition(source.userId(), request, postingId);
        } else {
            StudentRecommendationRequest request = source.type() == RecommendationMatchSourceType.SAVED_SEARCH
                    ? savedSearchService.tutorRequest(source.userId(), source.id())
                    : new StudentRecommendationRequest(null, source.id());
            found = studentRecommendationService.matchPosition(source.userId(), request, postingId);
        }
        if (found.isEmpty() || found.get().rank() > settings.getAlertTopRank()
                || found.get().score() < settings.getAlertNotifyThreshold()) {
            return false;
        }
        MatchPosition position = found.get();

        String key = "rec-match:" + source.type() + ":" + source.id() + ":" + postingId;
        Map<String, String> data = new HashMap<>();
        data.put("name", position.personName() == null ? "" : position.personName());
        data.put("title", position.title() == null ? "" : position.title());
        data.put("reason", position.reason() == null ? "" : position.reason());
        notificationService.createFromEvent(NotificationDomainEvent.deduplicated(
                source.userId(), NotificationEventType.RECOMMENDATION_MATCH_FOUND,
                teaching ? NotificationReferenceType.TEACHING_REQUEST : NotificationReferenceType.STUDYING_REQUEST,
                postingId, data, key));
        Notification notification = notificationRepository.findByDeduplicationKey(key).orElse(null);
        alertRepository.saveAndFlush(RecommendationMatchAlert.builder()
                .recipientUser(entityManager.getReference(User.class, source.userId()))
                .sourceType(source.type()).sourceId(source.id())
                .candidateType(candidateType).candidateId(postingId)
                .finalScore(BigDecimal.valueOf(position.score()).setScale(4, RoundingMode.HALF_UP))
                .candidateRank(position.rank())
                .notification(notification)
                .build());
        if (source.type() == RecommendationMatchSourceType.SAVED_SEARCH) {
            jdbc.update("UPDATE recommendation_saved_searches SET last_notified_at = ? WHERE id = ?",
                    java.sql.Timestamp.valueOf(now), source.id());
        }
        return true;
    }
}
