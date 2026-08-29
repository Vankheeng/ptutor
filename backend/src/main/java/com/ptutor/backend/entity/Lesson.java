package com.ptutor.backend.entity;

import java.time.LocalDate;
import java.time.LocalTime;

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

import com.ptutor.backend.entity.enums.LessonStatus;
import com.ptutor.backend.entity.enums.TeachingMode;

@Entity
@Table(name = "lessons")
@SQLDelete(sql = "UPDATE lessons SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@NoArgsConstructor
@AllArgsConstructor
@NonFinal
public class Lesson extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    @NonFinal
    private Contract contract;

    @Column(name = "title", columnDefinition = "varchar")
    @NonFinal
    private String title;

    @Column(name = "date")
    @NonFinal
    private LocalDate date;

    @Column(name = "start_time")
    @NonFinal
    private LocalTime startTime;

    @Column(name = "end_time")
    @NonFinal
    private LocalTime endTime;

    @Column(name = "teaching_mode", columnDefinition = "varchar")
    @NonFinal
    @Enumerated(EnumType.STRING)
    private TeachingMode teachingMode;

    @Column(name = "meeting_link", columnDefinition = "varchar")
    @NonFinal
    private String meetingLink;

    @Column(name = "location", columnDefinition = "varchar")
    @NonFinal
    private String location;

    @Column(name = "materials_url", columnDefinition = "varchar")
    @NonFinal
    private String materialsUrl;

    @Column(name = "status", columnDefinition = "varchar")
    @NonFinal
    @Enumerated(EnumType.STRING)
    private LessonStatus status;

    @Column(name = "note", columnDefinition = "text")
    @NonFinal
    private String note;
}
