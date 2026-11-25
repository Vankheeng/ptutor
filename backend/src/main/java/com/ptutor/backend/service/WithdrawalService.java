package com.ptutor.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.request.CreateWithdrawalRequest;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.dto.response.WithdrawalResponse;
import com.ptutor.backend.entity.Wallet;
import com.ptutor.backend.entity.WithdrawalRequest;
import com.ptutor.backend.entity.WalletSecurityChallenge;
import com.ptutor.backend.entity.enums.UserStatus;
import com.ptutor.backend.entity.enums.NotificationEventType;
import com.ptutor.backend.entity.enums.NotificationReferenceType;
import com.ptutor.backend.entity.enums.WithdrawalRequestStatus;
import com.ptutor.backend.event.NotificationDomainEvent;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.WithdrawalMapper;
import com.ptutor.backend.repository.EmployeeRepository;
import com.ptutor.backend.repository.WalletRepository;
import com.ptutor.backend.repository.WithdrawalRequestRepository;
import com.ptutor.backend.repository.WalletSecurityChallengeRepository;
import com.ptutor.backend.security.CurrentUserProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WithdrawalService {

    private final WalletRepository walletRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final WalletSecurityChallengeRepository challengeRepository;
    private final WithdrawalMapper withdrawalMapper;
    private final WalletService walletService;
    private final CurrentUserProvider currentUserProvider;
    private final EmployeeRepository employeeRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public WithdrawalResponse create(CreateWithdrawalRequest request, String rawKey) {
        throw new ApiException(HttpStatus.GONE, "WITHDRAWAL_OTP_REQUIRED",
                "Request a withdrawal verification code and confirm it before withdrawing");
    }

    @Transactional
    public WithdrawalResponse createVerified(
            CreateWithdrawalRequest request, String rawKey, Wallet wallet, WalletSecurityChallenge challenge) {
        String key = normalizeIdempotencyKey(rawKey);
        BigDecimal amount = normalize(request.amount());
        ensureActiveUser(wallet);

        var existing = withdrawalRequestRepository.findByWallet_IdAndIdempotencyKey(wallet.getId(), key);
        if (existing.isPresent()) {
            WithdrawalRequest prior = existing.get();
            if (prior.getAmount().compareTo(amount) != 0
                    || !prior.getBankCode().equals(challenge.getBankCode())
                    || !prior.getBankName().equals(challenge.getBankName())
                    || !prior.getEncryptedAccountNumber().equals(challenge.getEncryptedAccountNumber())
                    || !prior.getAccountHolderName().equals(challenge.getAccountHolderName())
                    || !Objects.equals(prior.getNote(), normalizeNote(request.note()))) {
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                        "Idempotency-Key was already used for a different withdrawal");
            }
            challenge.setWithdrawalRequest(prior);
            return withdrawalMapper.toResponse(existing.get());
        }

        WithdrawalRequest saved = withdrawalRequestRepository.saveAndFlush(WithdrawalRequest.builder()
                .wallet(wallet)
                .amount(amount)
                .bankCode(challenge.getBankCode())
                .bankName(challenge.getBankName())
                .encryptedAccountNumber(challenge.getEncryptedAccountNumber())
                .accountNumberLastFour(challenge.getAccountNumberLastFour())
                .accountHolderName(challenge.getAccountHolderName())
                .note(normalizeNote(request.note()))
                .idempotencyKey(key)
                .status(WithdrawalRequestStatus.PENDING)
                .build());

        walletService.reserveWithdrawalBalance(wallet, amount);

        WithdrawalRequest completed = withdrawalRequestRepository.saveAndFlush(saved);
        challenge.setWithdrawalRequest(completed);
        challengeRepository.save(challenge);
        publishWithdrawalNotice(completed, NotificationEventType.WITHDRAWAL_REQUEST_CREATED);
        publishReviewRequired(completed);
        return withdrawalMapper.toResponse(completed);
    }

    @Transactional(readOnly = true)
    public WithdrawalResponse getMine(UUID withdrawalId) {
        return withdrawalRequestRepository.findByIdAndWallet_User_Id(withdrawalId, currentUserProvider.getCurrentUserId())
                .map(withdrawalMapper::toResponse)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WITHDRAWAL_NOT_FOUND",
                        "Withdrawal request not found"));
    }

    @Transactional(readOnly = true)
    public PageResponse<WithdrawalResponse> findMine(int page, int size) {
        Page<WithdrawalRequest> values = withdrawalRequestRepository.findAllByWallet_User_Id(
                currentUserProvider.getCurrentUserId(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));

        return PageResponse.from(values, values.getContent().stream().map(withdrawalMapper::toResponse).toList());
    }

    @Transactional
    public WithdrawalResponse cancel(UUID withdrawalId) {
        Wallet wallet = walletRepository.findByUserIdForUpdate(currentUserProvider.getCurrentUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", "Wallet not found"));

        WithdrawalRequest withdrawalRequest = withdrawalRequestRepository.findByIdAndWallet_Id(withdrawalId, wallet.getId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "WITHDRAWAL_NOT_FOUND",
                        "Withdrawal request not found"));

        if (withdrawalRequest.getStatus() != WithdrawalRequestStatus.PENDING) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "WITHDRAWAL_NOT_CANCELLABLE",
                    "Only a pending withdrawal request can be cancelled");
        }

        walletService.releaseWithdrawalBalance(
                wallet, withdrawalRequest.getAmount(), withdrawalRequest.getWalletTransaction());
        withdrawalRequest.setStatus(WithdrawalRequestStatus.CANCELLED);
        WithdrawalRequest cancelled = withdrawalRequestRepository.saveAndFlush(withdrawalRequest);
        publishWithdrawalNotice(cancelled, NotificationEventType.WITHDRAWAL_REQUEST_CANCELLED);
        return withdrawalMapper.toResponse(cancelled);
    }

    private String normalizeIdempotencyKey(String rawKey) {
        String key = rawKey == null ? "" : rawKey.strip();
        if (key.isEmpty() || key.length() > 100) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key must contain 1 to 100 characters");
        }
        return key;
    }

    private void ensureActiveUser(Wallet wallet) {
        if (wallet.getUser().getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "USER_INACTIVE", "User account is not active");
        }
    }

    private BigDecimal normalize(BigDecimal value) {
        try {
            if (value == null || value.compareTo(new BigDecimal("50000")) < 0) {
                throw new ArithmeticException();
            }
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_WALLET_AMOUNT",
                    "Withdrawal amount must be at least 50,000 VND with at most two decimal places");
        }
    }

    private String normalizeNote(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private void publishReviewRequired(WithdrawalRequest request) {
        java.util.Map<String, String> data = java.util.Map.of(
                "amount", request.getAmount().stripTrailingZeros().toPlainString());
        employeeRepository.findAllUserIds().forEach(recipientUserId ->
                eventPublisher.publishEvent(NotificationDomainEvent.of(
                        recipientUserId,
                        NotificationEventType.WITHDRAWAL_REVIEW_REQUIRED,
                        NotificationReferenceType.WITHDRAWAL_REQUEST,
                        request.getId(),
                        data)));
    }

    private void publishWithdrawalNotice(WithdrawalRequest request, NotificationEventType eventType) {
        eventPublisher.publishEvent(NotificationDomainEvent.of(
                request.getWallet().getUser().getId(),
                eventType,
                NotificationReferenceType.WITHDRAWAL_REQUEST,
                request.getId(),
                java.util.Map.of("amount", request.getAmount().stripTrailingZeros().toPlainString())));
    }
}
