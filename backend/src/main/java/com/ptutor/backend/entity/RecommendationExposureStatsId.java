package com.ptutor.backend.entity;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.ptutor.backend.entity.enums.RecommendationCandidateType;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class RecommendationExposureStatsId implements Serializable {

    @Column(name = "candidate_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private RecommendationCandidateType candidateType;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;
}
