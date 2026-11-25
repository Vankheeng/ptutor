package com.ptutor.backend.entity;

import java.time.LocalTime;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Column;
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

@Entity
@Table(name = "studying_request_availabilities")
@SQLDelete(sql = "UPDATE studying_request_availabilities SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@NoArgsConstructor
@AllArgsConstructor
@NonFinal
public class StudyingRequestAvailability extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "studying_request_id", nullable = false)
    @NonFinal
    private StudyingRequest studyingRequest;

    @Column(name = "day_of_week", nullable = false)
    @NonFinal
    private Integer dayOfWeek;

    @Column(name = "start_time", nullable = false)
    @NonFinal
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    @NonFinal
    private LocalTime endTime;
}
