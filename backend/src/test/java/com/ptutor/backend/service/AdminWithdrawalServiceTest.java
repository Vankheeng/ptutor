package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.Wallet;
import com.ptutor.backend.entity.WalletTransaction;
import com.ptutor.backend.entity.WithdrawalRequest;
import com.ptutor.backend.entity.enums.WithdrawalRequestStatus;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.WithdrawalMapper;
import com.ptutor.backend.repository.UserRepository;
import com.ptutor.backend.repository.WalletRepository;
import com.ptutor.backend.repository.WithdrawalRequestRepository;
import com.ptutor.backend.security.BankAccountCryptoService;
import com.ptutor.backend.security.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
class AdminWithdrawalServiceTest {

    @Mock WithdrawalRequestRepository requests;
    @Mock WalletRepository wallets;
    @Mock UserRepository users;
    @Mock WalletService walletService;
    @Mock WithdrawalMapper mapper;
    @Mock BankAccountCryptoService crypto;
    @Mock CurrentUserProvider currentUser;
    @Mock ApplicationEventPublisher events;

    private AdminWithdrawalService service;
    private WithdrawalRequest request;
    private UUID id;
    private UUID ownerId;
    private UUID reviewerId;

    @BeforeEach
    void setUp() {
        service = new AdminWithdrawalService(requests, wallets, users, walletService, mapper, crypto,
                currentUser, events);
        id = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        reviewerId = UUID.randomUUID();
        User owner = User.builder().email("owner@example.com").firstName("An").build();
        owner.setId(ownerId);
        Wallet wallet = Wallet.builder().user(owner).balance(BigDecimal.ZERO)
                .pendingBalance(new BigDecimal("100000.00")).build();
        wallet.setId(UUID.randomUUID());
        request = WithdrawalRequest.builder().wallet(wallet).amount(new BigDecimal("100000.00"))
                .encryptedAccountNumber("encrypted").status(WithdrawalRequestStatus.PENDING).build();
        request.setId(id);
        when(requests.findOwnerId(id)).thenReturn(Optional.of(ownerId));
        when(wallets.findByUserIdForUpdate(ownerId)).thenReturn(Optional.of(wallet));
        when(requests.findById(id)).thenReturn(Optional.of(request));
        when(requests.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(crypto.decrypt("encrypted")).thenReturn("123456789");
    }

    @Test
    void approvalKeepsReservationAndPreventsRepeatedApproval() {
        User reviewer = User.builder().build();
        reviewer.setId(reviewerId);
        when(currentUser.getCurrentUserId()).thenReturn(reviewerId);
        when(users.findById(reviewerId)).thenReturn(Optional.of(reviewer));

        service.approve(id);

        assertThat(request.getStatus()).isEqualTo(WithdrawalRequestStatus.PROCESSING);
        assertThat(request.getReviewedByUser()).isSameAs(reviewer);
        assertThat(request.getWallet().getPendingBalance()).isEqualByComparingTo("100000.00");
        verify(walletService, never()).releaseWithdrawalBalance(any(), any(), any());
        assertThatThrownBy(() -> service.approve(id)).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectionReleasesReservedFunds() {
        User reviewer = User.builder().build();
        reviewer.setId(reviewerId);
        when(currentUser.getCurrentUserId()).thenReturn(reviewerId);
        when(users.findById(reviewerId)).thenReturn(Optional.of(reviewer));

        service.reject(id, "Sai thông tin ngân hàng");

        verify(walletService).releaseWithdrawalBalance(request.getWallet(), request.getAmount(), null);
        assertThat(request.getStatus()).isEqualTo(WithdrawalRequestStatus.REJECTED);
        assertThat(request.getRejectionReason()).isEqualTo("Sai thông tin ngân hàng");
    }

    @Test
    void completionRequiresProcessingAndRecordsTransferOnce() {
        assertThatThrownBy(() -> service.complete(id, "BANK-123")).isInstanceOf(ApiException.class);
        verify(walletService, never()).completeWithdrawalBalance(any(), any(), any(), any(), any());

        request.setStatus(WithdrawalRequestStatus.PROCESSING);
        WalletTransaction ledger = WalletTransaction.builder().build();
        when(walletService.completeWithdrawalBalance(any(), any(), any(), any(), any())).thenReturn(ledger);
        service.complete(id, " BANK-123 ");

        assertThat(request.getStatus()).isEqualTo(WithdrawalRequestStatus.COMPLETED);
        assertThat(request.getTransferReference()).isEqualTo("BANK-123");
        assertThat(request.getWalletTransaction()).isSameAs(ledger);
        assertThatThrownBy(() -> service.complete(id, "BANK-123")).isInstanceOf(ApiException.class);
    }
}
