package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.ptutor.backend.dto.recommendation.History;
import com.ptutor.backend.entity.RecommendationUserProfile;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.enums.RecommendationCandidateType;
import com.ptutor.backend.entity.enums.RecommendationProfileRole;
import com.ptutor.backend.repository.PreferenceSignalRepository;
import com.ptutor.backend.repository.PreferenceSignalRepository.Signal;
import com.ptutor.backend.repository.RecommendationUserProfileRepository;
import com.ptutor.backend.repository.StudentRepository;
import com.ptutor.backend.repository.TutorRepository;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.json.JsonMapper;

class PreferenceProfileServiceTest {

    private static final RecommendationCandidateType TYPE = RecommendationCandidateType.TEACHING_REQUEST;

    private final PreferenceSignalRepository signals = mock(PreferenceSignalRepository.class);
    private final RecommendationUserProfileRepository profiles = mock(RecommendationUserProfileRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final RecommendationSettings settings = new RecommendationSettings();
    private final PreferenceProfileService service = new PreferenceProfileService(signals, profiles,
            mock(StudentRepository.class), mock(TutorRepository.class), entityManager, JsonMapper.builder().build(),
            settings);

    private final UUID studentId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID liked = UUID.randomUUID();
    private final UUID disliked = UUID.randomUUID();
    private final LocalDateTime now = LocalDateTime.now();

    @BeforeEach
    void setUp() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        Student student = mock(Student.class);
        when(student.getUser()).thenReturn(user);
        when(entityManager.find(Student.class, studentId)).thenReturn(student);
        when(profiles.findById(userId)).thenReturn(Optional.empty());
    }

    @Test
    void buildsAVectorAndTagAffinitiesFromWhatTheStudentLikedAndDismissed() {
        when(signals.ofStudent(any(), any())).thenReturn(List.of(
                new Signal(liked, "VIEWED", now.minusDays(2)),
                new Signal(liked, "FAVORITED", now),
                new Signal(disliked, "DISMISSED", now)));
        when(signals.embeddings(any(), anyCollection()))
                .thenReturn(Map.of(liked, new float[] { 1, 0 }, disliked, new float[] { 0, 1 }));
        when(signals.tagCodes(any(), anyCollection())).thenReturn(Map.of(
                liked, Set.of("luyen_de", "kien_nhan"), disliked, Set.of("nghiem_khac", "kien_nhan")));

        assertThat(service.recompute(RecommendationProfileRole.STUDENT, studentId)).isTrue();

        ArgumentCaptor<RecommendationUserProfile> saved = ArgumentCaptor.forClass(RecommendationUserProfile.class);
        verify(profiles).save(saved.capture());
        RecommendationUserProfile profile = saved.getValue();
        assertThat(profile.getUserId()).isEqualTo(userId);
        assertThat(profile.getRole()).isEqualTo(RecommendationProfileRole.STUDENT);
        assertThat(profile.getSignalCount()).as("opening and then liking one posting is one signal").isEqualTo(1);
        // sum = 2 x (1, 0) - 2 x (0, 1), scaled to length 1
        assertThat(profile.getPreferenceEmbedding()[0]).isCloseTo((float) Math.sqrt(0.5), within(1e-6f));
        assertThat(profile.getPreferenceEmbedding()[1]).isCloseTo((float) -Math.sqrt(0.5), within(1e-6f));
        // total |weight| = 4: luyen_de +2, kien_nhan +2 - 2, nghiem_khac -2
        assertThat(profile.getTagAffinity().path("luyen_de").asDouble()).isCloseTo(0.5, within(1e-9));
        assertThat(profile.getTagAffinity().has("kien_nhan")).as("liked once and disliked once cancels out").isFalse();
        assertThat(profile.getTagAffinity().has("nghiem_khac")).isFalse();
    }

    @Test
    void oldBehaviourCountsLessAndBehaviourBeforeTheResetIsNotEvenRead() {
        assertThat(service.decay(0)).isEqualTo(1.0);
        assertThat(service.decay(30)).isCloseTo(0.5, within(1e-9));
        assertThat(service.decay(60)).isCloseTo(0.25, within(1e-9));

        LocalDateTime reset = now.minusDays(3);
        when(profiles.findById(userId)).thenReturn(Optional.of(RecommendationUserProfile.builder()
                .userId(userId).role(RecommendationProfileRole.STUDENT).historyResetAt(reset).build()));
        when(signals.ofStudent(any(), any())).thenReturn(List.of());

        service.recompute(RecommendationProfileRole.STUDENT, studentId);

        verify(signals).ofStudent(studentId, reset);
    }

    @Test
    void keepsTheStrongestSignalOfEachPostingAndLetsALaterDismissalWin() {
        UUID applied = UUID.randomUUID();
        UUID changedMind = UUID.randomUUID();
        UUID cameBack = UUID.randomUUID();

        List<Signal> kept = PreferenceProfileService.strongestPerPosting(List.of(
                new Signal(applied, "VIEWED", now.minusDays(3)),
                new Signal(applied, "ACCEPTED", now.minusDays(1)),
                new Signal(applied, "CONTACTED", now.minusDays(2)),
                new Signal(changedMind, "FAVORITED", now.minusDays(2)),
                new Signal(changedMind, "DISMISSED", now.minusDays(1)),
                new Signal(cameBack, "DISMISSED", now.minusDays(2)),
                new Signal(cameBack, "VIEWED", now.minusDays(1))), new RecommendationSettings().getHistorySignalWeights());

        assertThat(kept).extracting(Signal::candidateId, Signal::kind).containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple(applied, "ACCEPTED"),
                org.assertj.core.groups.Tuple.tuple(changedMind, "DISMISSED"),
                org.assertj.core.groups.Tuple.tuple(cameBack, "VIEWED"));
    }

    @Test
    void historyWeightGrowsWithTheNumberOfSignalsAndScoresResemblance() {
        UUID similar = UUID.randomUUID();
        UUID different = UUID.randomUUID();
        UUID noVector = UUID.randomUUID();
        float[] preference = { 1, 0 };
        when(profiles.findById(userId)).thenReturn(Optional.of(RecommendationUserProfile.builder()
                .userId(userId).role(RecommendationProfileRole.STUDENT).signalCount(4).preferenceEmbedding(preference)
                .tagAffinity(JsonMapper.builder().build().createObjectNode().put("luyen_de", 0.8).put("kien_nhan", 0.4))
                .build()));
        when(signals.cosines(TYPE, Set.of(similar, different, noVector), preference))
                .thenReturn(Map.of(similar, 0.80, different, 0.30));

        History history = service.historyFor(userId, TYPE, Map.of(
                similar, Set.of("luyen_de", "kien_nhan"), different, Set.of(), noVector, Set.of("luyen_de")));

        assertThat(history.weight()).as("4 of the 10 signals needed for the full 15 %").isCloseTo(0.06, within(1e-9));
        // 0.6 x 1.0 + 0.4 x (0.8 + 0.4) / max(1, 1.2)
        assertThat(history.of(similar)).isCloseTo(1.0, within(1e-9));
        assertThat(history.of(different)).isCloseTo(0.0, within(1e-9));
        // no vector: neutral 0.5 for that part; 0.6 x 0.5 + 0.4 x 0.8 / 1.2
        assertThat(history.of(noVector)).isCloseTo(0.3 + 0.4 * 0.8 / 1.2, within(1e-9));
    }

    @Test
    void thereIsNoHistoryForANewUserOrWhenSwitchedOff() {
        UUID candidate = UUID.randomUUID();
        assertThat(service.historyFor(userId, TYPE, Map.of(candidate, Set.of()))).isSameAs(History.NONE);

        when(profiles.findById(userId)).thenReturn(Optional.of(RecommendationUserProfile.builder()
                .userId(userId).role(RecommendationProfileRole.STUDENT).signalCount(0).build()));
        assertThat(service.historyFor(userId, TYPE, Map.of(candidate, Set.of()))).isSameAs(History.NONE);
        assertThat(History.NONE.of(candidate)).isNull();

        ReflectionTestUtils.setField(settings, "historyEnabled", false);
        assertThat(service.historyFor(userId, TYPE, Map.of(candidate, Set.of()))).isSameAs(History.NONE);
    }

    @Test
    void deletingTheHistoryEmptiesTheProfileAndRemembersWhen() {
        service.reset(userId, RecommendationProfileRole.STUDENT);

        ArgumentCaptor<RecommendationUserProfile> saved = ArgumentCaptor.forClass(RecommendationUserProfile.class);
        verify(profiles).save(saved.capture());
        assertThat(saved.getValue().getSignalCount()).isZero();
        assertThat(saved.getValue().getPreferenceEmbedding()).isNull();
        assertThat(saved.getValue().getTagAffinity().isEmpty()).isTrue();
        assertThat(saved.getValue().getHistoryResetAt()).isAfter(now.minusSeconds(5));
    }
}
