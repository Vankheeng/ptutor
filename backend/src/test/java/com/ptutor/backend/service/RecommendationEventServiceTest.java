package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ptutor.backend.dto.request.RecommendationEventRequest;
import com.ptutor.backend.entity.RecommendationLog;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.TeachingRequest;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.RecommendationAction;
import com.ptutor.backend.entity.enums.RecommendationDirection;
import com.ptutor.backend.repository.AiEnrichmentJobRepository;
import com.ptutor.backend.repository.FavoriteTutorRepository;
import com.ptutor.backend.repository.RecommendationClickLogRepository;
import com.ptutor.backend.repository.RecommendationLogRepository;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.json.JsonMapper;

class RecommendationEventServiceTest {

    private final RecommendationLogRepository logs = mock(RecommendationLogRepository.class);
    private final RecommendationClickLogRepository clicks = mock(RecommendationClickLogRepository.class);
    private final FavoriteTutorRepository favorites = mock(FavoriteTutorRepository.class);
    private final AiEnrichmentJobRepository jobs = mock(AiEnrichmentJobRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final RecommendationEventService service = new RecommendationEventService(logs, clicks, favorites, jobs,
            entityManager);

    @Test
    void togglingInterestPersistsAndRemovesTheTutorFavorite() {
        UUID userId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID tutorId = UUID.randomUUID();
        UUID logId = UUID.randomUUID();
        UUID postingId = UUID.randomUUID();

        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        Student student = mock(Student.class);
        when(student.getId()).thenReturn(studentId);
        when(student.getUser()).thenReturn(user);
        Tutor tutor = mock(Tutor.class);
        when(tutor.getId()).thenReturn(tutorId);
        TeachingRequest posting = mock(TeachingRequest.class);
        when(posting.getId()).thenReturn(postingId);
        when(posting.getTutor()).thenReturn(tutor);
        RecommendationLog log = mock(RecommendationLog.class);
        when(log.getDirection()).thenReturn(RecommendationDirection.STUDENT_TO_TUTOR);
        when(log.getSearcherStudent()).thenReturn(student);
        ArrayNode candidates = JsonMapper.builder().build().createArrayNode();
        candidates.addObject().put("candidateId", postingId.toString()).put("rank", 1);
        when(log.getReturnedCandidateIds()).thenReturn(candidates);
        when(logs.findById(logId)).thenReturn(Optional.of(log));
        when(entityManager.find(TeachingRequest.class, postingId)).thenReturn(posting);

        service.record(userId, logId, new RecommendationEventRequest(RecommendationAction.FAVORITED, null, postingId));
        service.record(userId, logId, new RecommendationEventRequest(RecommendationAction.DISMISSED, null, postingId));

        verify(favorites).addOrRestore(any(UUID.class), eq(studentId), eq(tutorId), eq(postingId), any(LocalDateTime.class));
        verify(favorites).remove(eq(studentId), eq(tutorId), any(LocalDateTime.class));
    }

    @Test
    void removesAnOlderFavoriteEvenWithoutRecommendationHistory() {
        UUID userId = UUID.randomUUID();
        UUID tutorId = UUID.randomUUID();
        when(clicks.findFirstByRecommendationLog_SearcherStudent_User_IdAndClickedTutor_IdOrderByCreatedAtDesc(
                userId, tutorId)).thenReturn(Optional.empty());

        service.removeFavoriteTutor(userId, tutorId);

        verify(favorites).removeForStudentUser(eq(userId), eq(tutorId), any(LocalDateTime.class));
    }

    @Test
    void readsPersistedFavoriteTutorIds() {
        UUID userId = UUID.randomUUID();
        UUID tutorId = UUID.randomUUID();
        when(favorites.findTutorUserIdsByStudentUserId(userId)).thenReturn(List.of(tutorId));

        assertThat(service.favoriteTutorUserIds(userId)).containsExactly(tutorId);
    }
}
