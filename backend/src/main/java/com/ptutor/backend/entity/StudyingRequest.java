package com.ptutor.backend.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;

import com.ptutor.backend.entity.enums.LearningMode;
import com.ptutor.backend.entity.enums.RequestStatus;

@Entity
@Table(name = "studying_requests")
@SQLDelete(sql = "UPDATE studying_requests SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@NoArgsConstructor
@AllArgsConstructor
@NonFinal
public class StudyingRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "district_id")
    @NonFinal
    private District district;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    @NonFinal
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    @NonFinal
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grade_id", nullable = false)
    @NonFinal
    private Grade grade;

    @Column(name = "quantity", nullable = false)
    @NonFinal
    private Integer quantity;

    @Column(name = "title", length = 255)
    @NonFinal
    private String title;

    @Column(name = "description", length = 255)
    @NonFinal
    private String description;

    @Column(name = "note", length = 255)
    @NonFinal
    private String note;

    @Column(name = "detail_address", length = 255)
    @NonFinal
    private String detailAddress;

    @Column(name = "min_price", precision = 15, scale = 2)
    @NonFinal
    private BigDecimal minPrice;

    @Column(name = "max_price", precision = 15, scale = 2)
    @NonFinal
    private BigDecimal maxPrice;

    @Column(name = "learning_goals", columnDefinition = "text")
    @NonFinal
    private String learningGoals;

    @Column(name = "learning_mode", nullable = false, length = 30)
    @NonFinal
    @Enumerated(EnumType.STRING)
    private LearningMode learningMode;

    @Column(name = "preferred_schedule", length = 500)
    @NonFinal
    private String preferredSchedule;

    @Column(name = "status", nullable = false, length = 30)
    @NonFinal
    @Enumerated(EnumType.STRING)
    private RequestStatus status;

    @OneToMany(mappedBy = "studyingRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dayOfWeek ASC, startTime ASC")
    @Builder.Default
    @NonFinal
    private List<StudyingRequestAvailability> availabilities = new ArrayList<>();
}
