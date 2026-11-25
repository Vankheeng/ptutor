package com.ptutor.backend.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

import com.ptutor.backend.entity.enums.CertificateStatus;

@Entity
@Table(name = "certificates")
@SQLDelete(sql = "UPDATE certificates SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@NoArgsConstructor
@AllArgsConstructor
@NonFinal
public class Certificate extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutor_id", nullable = false)
    @NonFinal
    private Tutor tutor;

    @Column(name = "name", nullable = false, length = 255)
    @NonFinal
    private String name;

    @Column(name = "issuing_organization", length = 255)
    @NonFinal
    private String issuingOrganization;

    @Column(name = "description", length = 255)
    @NonFinal
    private String description;

    @Column(name = "issue_date")
    @NonFinal
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    @NonFinal
    private LocalDate expiryDate;

    @Column(name = "certificate_url", length = 500)
    @NonFinal
    private String certificateUrl;

    @Column(name = "status", length = 30)
    @NonFinal
    @Enumerated(EnumType.STRING)
    private CertificateStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    @NonFinal
    private Employee reviewedBy;

    @Column(name = "reviewed_at")
    @NonFinal
    private LocalDateTime reviewedAt;

    @Column(name = "rejection_reason", length = 500)
    @NonFinal
    private String rejectionReason;

}
