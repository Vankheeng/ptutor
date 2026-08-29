package com.ptutor.backend.entity;

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

import com.ptutor.backend.entity.enums.CancelContractRequestStatus;

@Entity
@Table(name = "cancel_contract_requests")
@SQLDelete(sql = "UPDATE cancel_contract_requests SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@NoArgsConstructor
@AllArgsConstructor
@NonFinal
public class CancelContractRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    @NonFinal
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @NonFinal
    private User user;

    @Column(name = "reason", columnDefinition = "text")
    @NonFinal
    private String reason;

    @Column(name = "status", columnDefinition = "varchar")
    @NonFinal
    @Enumerated(EnumType.STRING)
    private CancelContractRequestStatus status;
}
