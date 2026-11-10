package com.ptutor.backend.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.recommendation.History;
import com.ptutor.backend.entity.RecommendationUserProfile;
import com.ptutor.backend.entity.Student;
import com.ptutor.backend.entity.Tutor;
import com.ptutor.backend.entity.enums.RecommendationCandidateType;
import com.ptutor.backend.entity.enums.RecommendationProfileRole;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.repository.PreferenceSignalRepository;
import com.ptutor.backend.repository.PreferenceSignalRepository.Signal;
import com.ptutor.backend.repository.RecommendationUserProfileRepository;
import com.ptutor.backend.repository.StudentRepository;
import com.ptutor.backend.repository.TutorRepository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * H1 (design 07, 7.4): a long-term picture of what one user likes, built only from that user's own
 * behaviour. It is a vector (the weighted average of the postings they showed interest in) and an
 * affinity per tag. Searches use it through the feature f_hist; it never filters anything out.
 */
@Service
@RequiredArgsConstructor
public class PreferenceProfileService {

    static final String DISMISSED = "DISMISSED";

    private final PreferenceSignalRepository signalRepository;
    private final RecommendationUserProfileRepository profileRepository;
    private final StudentRepository studentRepository;
    private final TutorRepository tutorRepository;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final RecommendationSettings settings;

    /**
     * Builds the profile again from the last 180 days of behaviour. Cheap (a few dozen rows), so it is
     * simply redone after every new signal and once a night to let old signals fade.
     *
     * @param entityId students.id or tutors.id, depending on the role
     * @return false when the person no longer exists
     */
    @Transactional
    public boolean recompute(RecommendationProfileRole role, UUID entityId) {
        UUID userId = userId(role, entityId);
        if (userId == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        RecommendationUserProfile profile = profileRepository.findById(userId).orElse(null);
        LocalDateTime since = now.minusDays(settings.getHistoryWindowDays());
        if (profile != null && profile.getHistoryResetAt() != null && profile.getHistoryResetAt().isAfter(since)) {
            since = profile.getHistoryResetAt();
        }
        boolean student = role == RecommendationProfileRole.STUDENT;
        RecommendationCandidateType type = student
                ? RecommendationCandidateType.TEACHING_REQUEST : RecommendationCandidateType.STUDYING_REQUEST;
        Map<String, Double> signalWeights = settings.getHistorySignalWeights();
        List<Signal> signals = strongestPerPosting(
                student ? signalRepository.ofStudent(entityId, since) : signalRepository.ofTutor(entityId, since),
                signalWeights);
        if (signals.isEmpty() && profile == null) {
            return true;
        }

        Set<UUID> postings = new java.util.HashSet<>();
        signals.forEach(signal -> postings.add(signal.candidateId()));
        Map<UUID, float[]> vectors = signalRepository.embeddings(type, postings);
        Map<UUID, Set<String>> tags = signalRepository.tagCodes(type, postings);

        double[] sum = null;
        Map<String, Double> tagSums = new HashMap<>();
        double totalWeight = 0;
        int positive = 0;
        for (Signal signal : signals) {
            double weight = signalWeights.getOrDefault(signal.kind(), 0.0);
            double decayed = weight * decay(Duration.between(signal.happenedAt(), now).toHours() / 24.0);
            if (weight > 0) {
                positive++;
            }
            totalWeight += Math.abs(decayed);
            float[] vector = vectors.get(signal.candidateId());
            if (vector != null) {
                sum = sum == null ? new double[vector.length] : sum;
                for (int index = 0; index < vector.length; index++) {
                    sum[index] += decayed * vector[index];
                }
            }
            for (String code : tags.getOrDefault(signal.candidateId(), Set.of())) {
                tagSums.merge(code, decayed, Double::sum);
            }
        }

        ObjectNode affinity = objectMapper.createObjectNode();
        for (Map.Entry<String, Double> entry : new java.util.TreeMap<>(tagSums).entrySet()) {
            double value = totalWeight == 0 ? 0 : Math.max(0, Math.min(1, entry.getValue() / totalWeight));
            if (value > 0) {
                affinity.put(entry.getKey(), Math.round(value * 10_000) / 10_000.0);
            }
        }
        if (profile == null) {
            profile = RecommendationUserProfile.builder().userId(userId).role(role).build();
        }
        profile.setPreferenceEmbedding(normalize(sum));
        profile.setTagAffinity(affinity);
        profile.setSignalCount(positive);
        profileRepository.save(profile);
        return true;
    }

    /**
     * f_hist of each candidate for this user (design 06, 6.4):
     * {@code 0.6 x s(cos(v_pref, posting)) + 0.4 x (affinity of the posting's tags / max(1, all affinity))}
     * (shares {@code history.cosine-share}, {@code history.tag-share}).
     *
     * @param candidateTagCodes the candidates of the search with the codes of their tags
     */
    @Transactional(readOnly = true)
    public History historyFor(
            UUID userId, RecommendationCandidateType type, Map<UUID, Set<String>> candidateTagCodes) {
        if (!settings.isHistoryEnabled() || userId == null || candidateTagCodes.isEmpty()) {
            return History.NONE;
        }
        Optional<RecommendationUserProfile> found = profileRepository.findById(userId);
        if (found.isEmpty() || found.get().getSignalCount() == null || found.get().getSignalCount() <= 0) {
            return History.NONE;
        }
        RecommendationUserProfile profile = found.get();
        double weight = settings.getHistoryMaxWeight()
                * Math.min(1.0, profile.getSignalCount() / (double) Math.max(1, settings.getHistoryFullWeightSignals()));
        Map<String, Double> affinity = affinity(profile.getTagAffinity());
        double allAffinity = Math.max(1.0, affinity.values().stream().mapToDouble(Double::doubleValue).sum());
        Map<UUID, Double> cosines =
                signalRepository.cosines(type, candidateTagCodes.keySet(), profile.getPreferenceEmbedding());

        Map<UUID, Double> byCandidate = new HashMap<>();
        candidateTagCodes.forEach((candidateId, codes) -> {
            Double cosine = cosines.get(candidateId);
            double vectorPart = cosine == null ? settings.getHistoryWithoutVector() : Math.max(0, Math.min(1,
                    (cosine - settings.getCosLow()) / (settings.getCosHigh() - settings.getCosLow())));
            double tagPart = codes.stream().mapToDouble(code -> affinity.getOrDefault(code, 0.0)).sum() / allAffinity;
            byCandidate.put(candidateId, settings.getHistoryCosineShare() * vectorPart
                    + settings.getHistoryTagShare() * Math.min(1.0, tagPart));
        });
        return new History(weight, byCandidate);
    }

    /** How much the user likes each tag, for the sentence that explains a result. Empty without a profile. */
    @Transactional(readOnly = true)
    public Map<String, Double> affinityOf(UUID userId) {
        return userId == null ? Map.of()
                : profileRepository.findById(userId).map(profile -> affinity(profile.getTagAffinity())).orElse(Map.of());
    }

    /** For the signed-in student or tutor. */
    @Transactional
    public void deleteHistory(UUID userId) {
        if (studentRepository.findByUser_Id(userId).isPresent()) {
            reset(userId, RecommendationProfileRole.STUDENT);
        } else if (tutorRepository.findByUser_Id(userId).isPresent()) {
            reset(userId, RecommendationProfileRole.TUTOR);
        } else {
            throw new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND",
                    "Only students and tutors have a recommendation history");
        }
    }

    /**
     * "Delete my recommendation history": the profile is emptied and everything the user did before
     * this moment is ignored from now on, so the next recomputation does not bring it back.
     */
    @Transactional
    public void reset(UUID userId, RecommendationProfileRole role) {
        RecommendationUserProfile profile = profileRepository.findById(userId)
                .orElseGet(() -> RecommendationUserProfile.builder().userId(userId).role(role).build());
        profile.setPreferenceEmbedding(null);
        profile.setTagAffinity(objectMapper.createObjectNode());
        profile.setSignalCount(0);
        profile.setHistoryResetAt(LocalDateTime.now());
        profileRepository.save(profile);
    }

    /**
     * One signal per posting: the strongest thing the user did with it (opening and then applying is
     * one sign of interest, not two). A dismissal wins over everything when it came last.
     *
     * @param weights how much each kind of signal counts ({@code history.signal-weights})
     */
    static List<Signal> strongestPerPosting(List<Signal> signals, Map<String, Double> weights) {
        Map<UUID, Signal> best = new java.util.LinkedHashMap<>();
        Map<UUID, Signal> dismissed = new HashMap<>();
        for (Signal signal : signals) {
            if (DISMISSED.equals(signal.kind())) {
                dismissed.merge(signal.candidateId(), signal,
                        (old, fresh) -> fresh.happenedAt().isAfter(old.happenedAt()) ? fresh : old);
            } else if (weights.containsKey(signal.kind())) {
                best.merge(signal.candidateId(), signal, (old, fresh) ->
                        weights.get(fresh.kind()) > weights.get(old.kind()) ? fresh : old);
            }
        }
        dismissed.forEach((posting, signal) -> {
            Signal positive = best.get(posting);
            if (positive == null || signal.happenedAt().isAfter(positive.happenedAt())) {
                best.put(posting, signal);
            }
        });
        return List.copyOf(best.values());
    }

    /** Half as much after every half-life: recent behaviour says more than old behaviour. */
    double decay(double ageDays) {
        return Math.pow(0.5, Math.max(0, ageDays) / settings.getHistoryHalfLifeDays());
    }

    private float[] normalize(double[] sum) {
        if (sum == null) {
            return null;
        }
        double norm = 0;
        for (double value : sum) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        if (norm < 1e-9) {
            return null;
        }
        float[] vector = new float[sum.length];
        for (int index = 0; index < sum.length; index++) {
            vector[index] = (float) (sum[index] / norm);
        }
        return vector;
    }

    private Map<String, Double> affinity(JsonNode stored) {
        Map<String, Double> affinity = new HashMap<>();
        if (stored != null && stored.isObject()) {
            stored.properties().forEach(entry -> {
                if (entry.getValue().isNumber()) {
                    affinity.put(entry.getKey(), entry.getValue().doubleValue());
                }
            });
        }
        return affinity;
    }

    private UUID userId(RecommendationProfileRole role, UUID entityId) {
        if (role == RecommendationProfileRole.STUDENT) {
            Student student = entityManager.find(Student.class, entityId);
            return student == null || student.getUser() == null ? null : student.getUser().getId();
        }
        Tutor tutor = entityManager.find(Tutor.class, entityId);
        return tutor == null || tutor.getUser() == null ? null : tutor.getUser().getId();
    }
}
