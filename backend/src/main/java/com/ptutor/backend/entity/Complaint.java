package com.ptutor.backend.entity;

import java.time.LocalDateTime;

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
@Table(name = "complaints")
@SQLDelete(sql = "UPDATE complaints SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@NoArgsConstructor
@AllArgsConstructor
@NonFinal
public class Complaint extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @NonFinal
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    @NonFinal
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    @NonFinal
    private Employee employee;

    @Column(name = "title", columnDefinition = "varchar")
    @NonFinal
    private String title;

    @Column(name = "content", columnDefinition = "text")
    @NonFinal
    private String content;

    @Column(name = "status", columnDefinition = "varchar")
    @NonFinal
    private String status;

    @Column(name = "resolution", columnDefinition = "text")
    @NonFinal
    private String resolution;

    @Column(name = "resolved_at")
    @NonFinal
    private LocalDateTime resolvedAt;
}
