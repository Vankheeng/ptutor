package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ptutor.backend.dto.request.UpdateBankAccountRequest;
import com.ptutor.backend.dto.response.BankAccountResponse;
import com.ptutor.backend.entity.BankAccount;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.Wallet;
import com.ptutor.backend.entity.enums.UserStatus;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.BankAccountMapper;
import com.ptutor.backend.repository.BankAccountRepository;
import com.ptutor.backend.repository.WalletRepository;
import com.ptutor.backend.security.BankAccountCryptoService;
import com.ptutor.backend.security.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
class BankAccountServiceTest {

    @Mock BankAccountRepository bankAccountRepository;
    @Mock WalletRepository walletRepository;
    @Mock BankAccountCryptoService bankAccountCryptoService;
    @Mock CurrentUserProvider currentUserProvider;

    private BankAccountService bankAccountService;
    private UUID userId;
    private User user;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        BankAccountMapper mapper = Mappers.getMapper(BankAccountMapper.class);
        bankAccountService = new BankAccountService(
                bankAccountRepository, walletRepository, mapper, bankAccountCryptoService, currentUserProvider);
        userId = UUID.randomUUID();
        user = User.builder().status(UserStatus.ACTIVE).build();
        user.setId(userId);
        wallet = Wallet.builder().user(user).build();
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

    @Test
    void updateCurrentBankAccountCreatesEncryptedAccountForCurrentUser() {
        UUID accountId = UUID.randomUUID();
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));
        when(bankAccountRepository.findByUser_Id(userId)).thenReturn(Optional.empty());
        when(bankAccountCryptoService.encrypt("1234567890")).thenReturn("encrypted-number");
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 10, 11, 25, 54);
        LocalDateTime updatedAt = createdAt.plusMinutes(1);
        when(bankAccountRepository.saveAndFlush(any(BankAccount.class))).thenAnswer(invocation -> {
            BankAccount account = invocation.getArgument(0);
            account.setId(accountId);
            account.setCreatedAt(createdAt);
            account.setUpdatedAt(updatedAt);
            return account;
        });

        BankAccountResponse response = bankAccountService.updateCurrentBankAccount(
                new UpdateBankAccountRequest("vcb", "  Vietcombank  ", "1234567890", " Nguyen   Van A "));

        ArgumentCaptor<BankAccount> captor = ArgumentCaptor.forClass(BankAccount.class);
        verify(bankAccountRepository).saveAndFlush(captor.capture());
        BankAccount savedAccount = captor.getValue();
        assertThat(savedAccount.getUser()).isSameAs(user);
        assertThat(savedAccount.getBankCode()).isEqualTo("VCB");
        assertThat(savedAccount.getBankName()).isEqualTo("Vietcombank");
        assertThat(savedAccount.getEncryptedAccountNumber()).isEqualTo("encrypted-number");
        assertThat(savedAccount.getAccountNumberLastFour()).isEqualTo("7890");
        assertThat(savedAccount.getAccountHolderName()).isEqualTo("Nguyen Van A");
        assertThat(response.bankAccountId()).isEqualTo(accountId);
        assertThat(response.bankCode()).isEqualTo("VCB");
        assertThat(response.bankName()).isEqualTo("Vietcombank");
        assertThat(response.maskedAccountNumber()).isEqualTo("****7890");
        assertThat(response.accountHolderName()).isEqualTo("Nguyen Van A");
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void updateCurrentBankAccountRejectsMissingWallet() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bankAccountService.updateCurrentBankAccount(
                new UpdateBankAccountRequest("VCB", "Vietcombank", "1234567890", "Nguyen Van A")))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("WALLET_NOT_FOUND"));
    }
}
