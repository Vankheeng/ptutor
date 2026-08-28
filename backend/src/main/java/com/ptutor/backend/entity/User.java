package com.ptutor.backend.entity;

import java.time.LocalDate;

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

    @Column(name = "gender", length = 20)
    @NonFinal
    private String gender;

    @Column(name = "detail_address", length = 255)
    @NonFinal
    private String detailAddress;

    @Column(name = "avatar_url", length = 500)
    @NonFinal
    private String avatarUrl;

    @Column(name = "status", nullable = false, length = 30)
    @NonFinal
    private String status;

}
