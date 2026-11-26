package com.ptutor.backend.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.response.AdminWithdrawalResponse;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.entity.User;
import com.ptutor.backend.entity.Wallet;
import com.ptutor.backend.entity.WithdrawalRequest;
import com.ptutor.backend.entity.enums.NotificationEventType;
import com.ptutor.backend.entity.enums.NotificationReferenceType;
import com.ptutor.backend.entity.enums.WithdrawalRequestStatus;
import com.ptutor.backend.event.NotificationDomainEvent;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.WithdrawalMapper;
import com.ptutor.backend.repository.UserRepository;
import com.ptutor.backend.repository.WalletRepository;
import com.ptutor.backend.repository.WithdrawalRequestRepository;
import com.ptutor.backend.security.BankAccountCryptoService;
import com.ptutor.backend.security.CurrentUserProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminWithdrawalService {

    private final WithdrawalRequestRepository requests;
    private final WalletRepository wallets;
    private final UserRepository users;
    private final WalletService walletService;
    private final WithdrawalMapper mapper;
    private final BankAccountCryptoService crypto;
    private final CurrentUserProvider currentUser;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public PageResponse<AdminWithdrawalResponse> findAll(WithdrawalRequestStatus status, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<WithdrawalRequest> values = status == null ? requests.findAll(pageable) : requests.findAllByStatus(status, pageable);
        return PageResponse.from(values, values.getContent().stream().map(value -> response(value, false)).toList());
    }

    @Transactional(readOnly = true)
    public AdminWithdrawalResponse findById(UUID id) {
        return response(find(id), true);
    }

    @Transactional
    public AdminWithdrawalResponse approve(UUID id) {
        WithdrawalRequest request = lockedRequest(id);
        requireStatus(request, WithdrawalRequestStatus.PENDING);
        request.setStatus(WithdrawalRequestStatus.PROCESSING);
        request.setReviewedByUser(reviewer());
        request.setReviewedAt(LocalDateTime.now());
        events.publishEvent(notice(request, NotificationEventType.WITHDRAWAL_REQUEST_APPROVED));
        return response(requests.saveAndFlush(request), true);
    }

    @Transactional
    public AdminWithdrawalResponse reject(UUID id, String reason) {
        WithdrawalRequest request = lockedRequest(id);
        requireStatus(request, WithdrawalRequestStatus.PENDING);
        String normalized = reason == null ? "" : reason.strip();
        if (normalized.isEmpty() || normalized.length() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REJECTION_REASON", "Rejection reason is required");
        }
        walletService.releaseWithdrawalBalance(request.getWallet(), request.getAmount(), request.getWalletTransaction());
        request.setStatus(WithdrawalRequestStatus.REJECTED);
        request.setReviewedByUser(reviewer());
        request.setReviewedAt(LocalDateTime.now());
        request.setRejectionReason(normalized);
        events.publishEvent(notice(request, NotificationEventType.WITHDRAWAL_REQUEST_REJECTED));
        return response(requests.saveAndFlush(request), true);
    }

    /** Call only after the bank transfer has succeeded and its reference is known. */
    @Transactional
    public AdminWithdrawalResponse complete(UUID id, String transferReference) {
        WithdrawalRequest request = lockedRequest(id);
        requireStatus(request, WithdrawalRequestStatus.PROCESSING);
        String reference = transferReference == null ? "" : transferReference.strip();
        if (reference.isEmpty() || reference.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TRANSFER_REFERENCE", "Transfer reference is required");
        }
        request.setWalletTransaction(walletService.completeWithdrawalBalance(
                request.getWallet(), request.getId(), request.getAmount(), "withdrawal:" + request.getId(),
                request.getWalletTransaction()));
        request.setStatus(WithdrawalRequestStatus.COMPLETED);
        request.setCompletedAt(LocalDateTime.now());
        request.setTransferReference(reference);
        events.publishEvent(notice(request, NotificationEventType.WITHDRAWAL_REQUEST_COMPLETED));
        return response(requests.saveAndFlush(request), true);
    }

    private WithdrawalRequest lockedRequest(UUID id) {
        UUID ownerId = requests.findOwnerId(id).orElseThrow(() -> notFound());
        Wallet wallet = wallets.findByUserIdForUpdate(ownerId).orElseThrow(() -> notFound());
        WithdrawalRequest request = find(id);
        if (!request.getWallet().getId().equals(wallet.getId())) throw notFound();
        return request;
    }

    private WithdrawalRequest find(UUID id) {
        return requests.findById(id).orElseThrow(this::notFound);
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "WITHDRAWAL_NOT_FOUND", "Withdrawal request not found");
    }

    private void requireStatus(WithdrawalRequest request, WithdrawalRequestStatus expected) {
        if (request.getStatus() != expected) {
            throw new ApiException(HttpStatus.CONFLICT, "WITHDRAWAL_STATUS_CHANGED",
                    "Withdrawal request is no longer " + expected);
        }
    }

    private User reviewer() {
        return users.findById(currentUser.getCurrentUserId()).orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "Reviewer not found"));
    }

    private AdminWithdrawalResponse response(WithdrawalRequest request, boolean revealAccount) {
        User owner = request.getWallet().getUser();
        String name = java.util.stream.Stream.of(owner.getFirstName(), owner.getLastName())
                .filter(part -> part != null && !part.isBlank()).collect(java.util.stream.Collectors.joining(" "));
        return new AdminWithdrawalResponse(mapper.toResponse(request), owner.getId(), name, owner.getEmail(),
                revealAccount ? crypto.decrypt(request.getEncryptedAccountNumber()) : null);
    }

    private NotificationDomainEvent notice(WithdrawalRequest request, NotificationEventType type) {
        return NotificationDomainEvent.of(request.getWallet().getUser().getId(), type,
                NotificationReferenceType.WITHDRAWAL_REQUEST, request.getId(),
                java.util.Map.of("amount", request.getAmount().stripTrailingZeros().toPlainString()));
    }
}
