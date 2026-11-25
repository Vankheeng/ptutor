package com.ptutor.backend.entity;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;

import com.ptutor.backend.entity.enums.CatalogStatus;

@Entity
@Table(name = "subjects")
@SQLDelete(sql = "UPDATE subjects SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@NoArgsConstructor
@AllArgsConstructor
@NonFinal
public class Subject extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true, length = 100)
    @NonFinal
    private String name;

    @Column(name = "description", length = 500)
    @NonFinal
    private String description;

    @Column(name = "status", nullable = false, length = 30)
    @NonFinal
    @Enumerated(EnumType.STRING)
    private CatalogStatus status;
}
