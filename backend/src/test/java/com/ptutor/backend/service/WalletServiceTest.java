package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ptutor.backend.dto.response.WalletBalanceResponse;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.Wallet;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.WalletMapper;
import com.ptutor.backend.repository.WalletRepository;
import com.ptutor.backend.security.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock WalletRepository walletRepository;
    @Mock CurrentUserProvider currentUserProvider;

    private WalletService walletService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        WalletMapper mapper = Mappers.getMapper(WalletMapper.class);
        walletService = new WalletService(walletRepository, mapper, currentUserProvider);
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
}
