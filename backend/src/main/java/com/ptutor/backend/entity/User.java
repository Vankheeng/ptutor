package com.ptutor.backend.entity;

import java.time.LocalDate;

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

import com.ptutor.backend.entity.enums.Gender;
import com.ptutor.backend.entity.enums.UserStatus;

@Entity
@Table(name = "users")
@SQLDelete(sql = "UPDATE users SET deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@NoArgsConstructor
@AllArgsConstructor
@NonFinal
public class User extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "district_id")
    @NonFinal
    private District district;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    @NonFinal
    private String email;

    @Column(name = "citizen_id", nullable = false, length = 255)
    @NonFinal
    private String encryptedCitizenId;

    @Column(name = "citizen_id_hash", length = 64)
    @NonFinal
    private String citizenIdHash;

    @Column(name = "password", nullable = false, length = 255)
    @NonFinal
    private String password;

    @Column(name = "first_name", length = 100)
    @NonFinal
    private String firstName;

    @Column(name = "last_name", length = 100)
    @NonFinal
    private String lastName;

    @Column(name = "phone", length = 20)
    @NonFinal
    private String phone;

    @Column(name = "date_of_birth")
    @NonFinal
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 20)
    @NonFinal
    private Gender gender;

    @Column(name = "detail_address", length = 255)
    @NonFinal
    private String detailAddress;

    @Column(name = "avatar_url", length = 500)
    @NonFinal
    private String avatarUrl;

    @Column(name = "status", nullable = false, length = 30)
    @NonFinal
    @Enumerated(EnumType.STRING)
    private UserStatus status;

}
