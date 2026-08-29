package com.ptutor.backend.entity;

import java.math.BigDecimal;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;

import com.ptutor.backend.entity.enums.RequestStatus;
import com.ptutor.backend.entity.enums.TeachingMode;

@Entity
@Table(name = "teaching_requests")
@SQLDelete(sql = "UPDATE teaching_requests SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@NoArgsConstructor
@AllArgsConstructor
@NonFinal
public class TeachingRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutor_id", nullable = false)
    @NonFinal
    private Tutor tutor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    @NonFinal
    private Subject subject;

    @Column(name = "title", length = 255)
    @NonFinal
    private String title;

    @Column(name = "note", length = 255)
    @NonFinal
    private String note;

    @Column(name = "quantity")
    @NonFinal
    private Integer quantity;

    @Column(name = "detail_address", length = 255)
    @NonFinal
    private String detailAddress;

    @Column(name = "expected_price", precision = 15, scale = 2)
    @NonFinal
    private BigDecimal expectedPrice;

    @Column(name = "teaching_mode", nullable = false, length = 30)
    @NonFinal
    @Enumerated(EnumType.STRING)
    private TeachingMode teachingMode;

    @Column(name = "preferred_schedule", length = 500)
    @NonFinal
    private String preferredSchedule;

    @Column(name = "description", columnDefinition = "text")
    @NonFinal
    private String description;

    @Column(name = "status", nullable = false, length = 30)
    @NonFinal
    @Enumerated(EnumType.STRING)
    private RequestStatus status;
}
