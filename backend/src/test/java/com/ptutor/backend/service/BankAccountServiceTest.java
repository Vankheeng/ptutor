package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ptutor.backend.dto.response.BankAccountResponse;
import com.ptutor.backend.entity.BankAccount;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.mapper.BankAccountMapper;
import com.ptutor.backend.repository.BankAccountRepository;
import com.ptutor.backend.security.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
class BankAccountServiceTest {

    @Mock BankAccountRepository bankAccountRepository;
    @Mock CurrentUserProvider currentUserProvider;

    private BankAccountService bankAccountService;
    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        BankAccountMapper mapper = Mappers.getMapper(BankAccountMapper.class);
        bankAccountService = new BankAccountService(bankAccountRepository, mapper, currentUserProvider);
        userId = UUID.randomUUID();
        user = User.builder().build();
        user.setId(userId);
    }

    @Test
    void getCurrentBankAccountReturnsMaskedAccountNumber() {
        BankAccount account = BankAccount.builder()
                .user(user)
                .bankCode("VCB")
                .bankName("Vietcombank")
                .encryptedAccountNumber("encrypted")
                .accountNumberLastFour("6789")
                .accountHolderName("NGUYEN VAN A")
                .build();
        account.setId(UUID.randomUUID());
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
        when(bankAccountRepository.findByUser_Id(userId)).thenReturn(Optional.of(account));

        BankAccountResponse response = bankAccountService.getCurrentBankAccount();

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.bankCode()).isEqualTo("VCB");
        assertThat(response.bankName()).isEqualTo("Vietcombank");
        assertThat(response.maskedAccountNumber()).isEqualTo("****6789");
        assertThat(response.accountHolderName()).isEqualTo("NGUYEN VAN A");
    }

    @Test
    void getCurrentBankAccountReturnsNullWhenNotConfigured() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
        when(bankAccountRepository.findByUser_Id(userId)).thenReturn(Optional.empty());

        assertThat(bankAccountService.getCurrentBankAccount()).isNull();
    }

}
