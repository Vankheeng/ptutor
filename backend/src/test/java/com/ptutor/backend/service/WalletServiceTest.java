package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ptutor.backend.dto.response.WalletBalanceResponse;
import com.ptutor.backend.dto.command.WalletTransactionCommand;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.Wallet;
import com.ptutor.backend.entity.WalletTransaction;
import com.ptutor.backend.entity.enums.ReferenceType;
import com.ptutor.backend.entity.enums.UserStatus;
import com.ptutor.backend.entity.enums.WalletTransactionPurpose;
import com.ptutor.backend.entity.enums.WalletTransactionStatus;
import com.ptutor.backend.entity.enums.WalletTransactionType;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.WalletMapper;
import com.ptutor.backend.mapper.WalletTransactionMapper;
import com.ptutor.backend.repository.WalletRepository;
import com.ptutor.backend.repository.WalletTransactionRepository;
import com.ptutor.backend.security.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock WalletRepository walletRepository;
    @Mock WalletTransactionRepository walletTransactionRepository;
    @Mock WalletTransactionMapper walletTransactionMapper;
    @Mock CurrentUserProvider currentUserProvider;

    private WalletService walletService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        WalletMapper mapper = Mappers.getMapper(WalletMapper.class);
        walletService = new WalletService(
                walletRepository,
                walletTransactionRepository,
                mapper,
                walletTransactionMapper,
                currentUserProvider);
        userId = UUID.randomUUID();
    }

    @Test
    void getCurrentBalanceReturnsOnlyCurrentUsersWallet() {
        User user = User.builder().build();
        user.setId(userId);
        Wallet wallet = Wallet.builder()
                .user(user)
                .balance(new BigDecimal("150000.00"))
                .pendingBalance(new BigDecimal("50000.00"))
                .build();
        UUID walletId = UUID.randomUUID();
        wallet.setId(walletId);
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
        when(walletRepository.findByUser_Id(userId)).thenReturn(Optional.of(wallet));

        WalletBalanceResponse response = walletService.getCurrentBalance();

        assertThat(response.walletId()).isEqualTo(walletId);
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.balance()).isEqualByComparingTo("150000.00");
        assertThat(response.pendingBalance()).isEqualByComparingTo("50000.00");
        assertThat(response.currency()).isEqualTo("VND");
    }

    @Test
    void getCurrentBalanceRejectsMissingWallet() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
        when(walletRepository.findByUser_Id(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(walletService::getCurrentBalance)
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("WALLET_NOT_FOUND"));
    }

    @Test
    void creditAddsBalanceAndCreatesCompletedCreditTransaction() {
        User user = User.builder().status(UserStatus.ACTIVE).build();
        user.setId(userId);
        Wallet wallet = Wallet.builder()
                .user(user)
                .balance(new BigDecimal("100000.00"))
                .pendingBalance(BigDecimal.ZERO)
                .build();
        UUID walletId = UUID.randomUUID();
        wallet.setId(walletId);

        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));
        when(walletTransactionRepository.findByWallet_IdAndIdempotencyKey(walletId, "top-up-1"))
                .thenReturn(Optional.empty());
        when(walletTransactionRepository.saveAndFlush(any(WalletTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        walletService.credit(new WalletTransactionCommand(
                userId,
                new BigDecimal("50000.00"),
                WalletTransactionPurpose.TOP_UP,
                ReferenceType.PAYMENT,
                UUID.randomUUID(),
                "Top up confirmed",
                "top-up-1"));

        ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository).saveAndFlush(captor.capture());
        WalletTransaction transaction = captor.getValue();
        assertThat(wallet.getBalance()).isEqualByComparingTo("150000.00");
        assertThat(transaction.getTransactionType()).isEqualTo(WalletTransactionType.CREDIT);
        assertThat(transaction.getPurpose()).isEqualTo(WalletTransactionPurpose.TOP_UP);
        assertThat(transaction.getStatus()).isEqualTo(WalletTransactionStatus.COMPLETED);
        assertThat(transaction.getBalanceAfter()).isEqualByComparingTo("150000.00");
        assertThat(transaction.getPendingBalanceAfter()).isEqualByComparingTo("0.00");
    }

    @Test
    void reserveAndReverseWithdrawalCreatesDebitAndReversalTransactions() {
        User user = User.builder().status(UserStatus.ACTIVE).build();
        user.setId(userId);
        Wallet wallet = Wallet.builder()
                .user(user)
                .balance(new BigDecimal("500000.00"))
                .pendingBalance(BigDecimal.ZERO)
                .build();
        UUID walletId = UUID.randomUUID();
        UUID withdrawalId = UUID.randomUUID();
        wallet.setId(walletId);
        when(walletTransactionRepository.findByWallet_IdAndIdempotencyKey(walletId, "withdrawal-1"))
                .thenReturn(Optional.empty());
        when(walletTransactionRepository.findByWallet_IdAndIdempotencyKey(
                walletId, "withdrawal-reversal-" + withdrawalId)).thenReturn(Optional.empty());
        when(walletTransactionRepository.saveAndFlush(any(WalletTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WalletTransaction withdrawal = walletService.reserveForWithdrawal(
                wallet, withdrawalId, new BigDecimal("200000.00"), "withdrawal-1", "Withdrawal request");
        WalletTransaction reversal = walletService.reverseWithdrawal(wallet, withdrawal, withdrawalId);

        assertThat(withdrawal.getTransactionType()).isEqualTo(WalletTransactionType.DEBIT);
        assertThat(withdrawal.getPurpose()).isEqualTo(WalletTransactionPurpose.WITHDRAWAL);
        assertThat(withdrawal.getStatus()).isEqualTo(WalletTransactionStatus.CANCELLED);
        assertThat(reversal.getTransactionType()).isEqualTo(WalletTransactionType.CREDIT);
        assertThat(reversal.getPurpose()).isEqualTo(WalletTransactionPurpose.WITHDRAWAL_REVERSAL);
        assertThat(wallet.getBalance()).isEqualByComparingTo("500000.00");
        assertThat(wallet.getPendingBalance()).isEqualByComparingTo("0.00");
    }
}
