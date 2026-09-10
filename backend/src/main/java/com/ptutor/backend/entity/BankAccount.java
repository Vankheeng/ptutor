package com.ptutor.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
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
@Table(name = "bank_accounts")
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@NoArgsConstructor
@AllArgsConstructor
@NonFinal
public class BankAccount extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @NonFinal
    private User user;

    @Column(name = "bank_code", nullable = false, length = 20)
    @NonFinal
    private String bankCode;

    @Column(name = "bank_name", nullable = false, length = 100)
    @NonFinal
    private String bankName;

    @Column(name = "encrypted_account_number", nullable = false, length = 512)
    @NonFinal
    private String encryptedAccountNumber;

    @Column(name = "account_number_last_four", nullable = false, length = 4)
    @NonFinal
    private String accountNumberLastFour;

    @Column(name = "account_holder_name", nullable = false, length = 150)
    @NonFinal
    private String accountHolderName;
}
