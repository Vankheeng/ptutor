package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.Wallet;
import com.ptutor.backend.entity.WalletSecurityChallenge;
import com.ptutor.backend.entity.WithdrawalRequest;
import com.ptutor.backend.entity.enums.NotificationEventType;
import com.ptutor.backend.entity.enums.UserStatus;
import com.ptutor.backend.event.NotificationDomainEvent;
import com.ptutor.backend.mapper.WithdrawalMapper;
import com.ptutor.backend.repository.EmployeeRepository;
import com.ptutor.backend.repository.WalletRepository;
import com.ptutor.backend.repository.WalletSecurityChallengeRepository;
import com.ptutor.backend.repository.WithdrawalRequestRepository;
import com.ptutor.backend.security.CurrentUserProvider;

@ExtendWith(MockitoExtension.class)
class WithdrawalServiceTest {

    @Mock WalletRepository walletRepository;
    @Mock WithdrawalRequestRepository withdrawalRequestRepository;
    @Mock WalletSecurityChallengeRepository challengeRepository;
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
                withdrawalRequestRepository,
                challengeRepository,
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
        WalletSecurityChallenge challenge = WalletSecurityChallenge.builder()
                .user(wallet.getUser())
                .amount(new BigDecimal("100000.00"))
                .bankCode("VCB")
                .bankName("Vietcombank")
                .encryptedAccountNumber("encrypted")
                .accountNumberLastFour("1234")
                .accountHolderName("TEST USER")
                .idempotencyKey("withdraw-1")
                .build();
        challenge.setId(UUID.randomUUID());
        when(withdrawalRequestRepository.findByWallet_IdAndIdempotencyKey(wallet.getId(), "withdraw-1"))
                .thenReturn(Optional.empty());
        when(withdrawalRequestRepository.saveAndFlush(any(WithdrawalRequest.class)))
                .thenAnswer(invocation -> {
                    WithdrawalRequest value = invocation.getArgument(0);
                    if (value.getId() == null) {
                        value.setId(UUID.randomUUID());
                    }
                    return value;
                });
        when(challengeRepository.save(any(WalletSecurityChallenge.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(employeeRepository.findAllUserIds()).thenReturn(List.of(employeeUserId));
        when(withdrawalMapper.toResponse(any())).thenReturn(org.mockito.Mockito.mock(WithdrawalResponse.class));

        service.createVerified(new CreateWithdrawalRequest(new BigDecimal("100000.00"), null), "withdraw-1", wallet, challenge);

        ArgumentCaptor<NotificationDomainEvent> captor = ArgumentCaptor.forClass(NotificationDomainEvent.class);
        verify(eventPublisher, org.mockito.Mockito.times(2)).publishEvent(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(NotificationDomainEvent::recipientUserId)
                .containsExactly(userId, employeeUserId);
        assertThat(captor.getAllValues())
                .extracting(NotificationDomainEvent::eventType)
                .containsExactly(NotificationEventType.WITHDRAWAL_REQUEST_CREATED,
                        NotificationEventType.WITHDRAWAL_REVIEW_REQUIRED);
    }
}
