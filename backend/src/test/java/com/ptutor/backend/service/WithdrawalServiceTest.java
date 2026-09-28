package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.ptutor.backend.dto.request.CreateWithdrawalRequest;
import com.ptutor.backend.dto.response.WithdrawalResponse;
import com.ptutor.backend.entity.BankAccount;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.Wallet;
import com.ptutor.backend.entity.WalletTransaction;
import com.ptutor.backend.entity.WithdrawalRequest;
import com.ptutor.backend.entity.enums.NotificationEventType;
import com.ptutor.backend.entity.enums.UserStatus;
import com.ptutor.backend.event.NotificationDomainEvent;
import com.ptutor.backend.mapper.WithdrawalMapper;
import com.ptutor.backend.repository.BankAccountRepository;
import com.ptutor.backend.repository.EmployeeRepository;
import com.ptutor.backend.repository.WalletRepository;
import com.ptutor.backend.repository.WithdrawalRequestRepository;
import com.ptutor.backend.security.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
class WithdrawalServiceTest {

    @Mock WalletRepository walletRepository;
    @Mock BankAccountRepository bankAccountRepository;
    @Mock WithdrawalRequestRepository withdrawalRequestRepository;
    @Mock WithdrawalMapper withdrawalMapper;
    @Mock WalletService walletService;
    @Mock CurrentUserProvider currentUserProvider;
    @Mock EmployeeRepository employeeRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    private WithdrawalService service;
    private UUID userId;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        service = new WithdrawalService(
                walletRepository,
                bankAccountRepository,
                withdrawalRequestRepository,
                withdrawalMapper,
                walletService,
                currentUserProvider,
                employeeRepository,
                eventPublisher);
        userId = UUID.randomUUID();
        User user = User.builder().status(UserStatus.ACTIVE).build();
        user.setId(userId);
        wallet = Wallet.builder()
                .user(user)
                .balance(new BigDecimal("500000.00"))
                .pendingBalance(BigDecimal.ZERO.setScale(2))
                .build();
        wallet.setId(UUID.randomUUID());
    }

    @Test
    void createNotifiesEmployeesThatWithdrawalNeedsReview() {
        UUID employeeUserId = UUID.randomUUID();
        BankAccount bankAccount = BankAccount.builder()
                .user(wallet.getUser())
                .bankCode("VCB")
                .bankName("Vietcombank")
                .encryptedAccountNumber("encrypted")
                .accountNumberLastFour("1234")
                .accountHolderName("TEST USER")
                .build();
        when(currentUserProvider.getCurrentUserId()).thenReturn(userId);
        when(walletRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(wallet));
        when(withdrawalRequestRepository.findByWallet_IdAndIdempotencyKey(wallet.getId(), "withdraw-1"))
                .thenReturn(Optional.empty());
        when(bankAccountRepository.findByUser_Id(userId)).thenReturn(Optional.of(bankAccount));
        when(withdrawalRequestRepository.saveAndFlush(any(WithdrawalRequest.class)))
                .thenAnswer(invocation -> {
                    WithdrawalRequest value = invocation.getArgument(0);
                    if (value.getId() == null) {
                        value.setId(UUID.randomUUID());
                    }
                    return value;
                });
        when(walletService.reserveForWithdrawal(any(), any(), any(), any(), any()))
                .thenReturn(new WalletTransaction());
        when(employeeRepository.findAllUserIds()).thenReturn(List.of(employeeUserId));
        when(withdrawalMapper.toResponse(any())).thenReturn(org.mockito.Mockito.mock(WithdrawalResponse.class));

        service.create(new CreateWithdrawalRequest(new BigDecimal("100000.00"), null), "withdraw-1");

        ArgumentCaptor<NotificationDomainEvent> captor = ArgumentCaptor.forClass(NotificationDomainEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().recipientUserId()).isEqualTo(employeeUserId);
        assertThat(captor.getValue().eventType()).isEqualTo(NotificationEventType.WITHDRAWAL_REVIEW_REQUIRED);
    }
}
