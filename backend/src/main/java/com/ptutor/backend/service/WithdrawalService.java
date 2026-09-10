package com.ptutor.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.request.CreateWithdrawalRequest;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.dto.response.WithdrawalResponse;
import com.ptutor.backend.entity.BankAccount;
import com.ptutor.backend.entity.Wallet;
import com.ptutor.backend.entity.WithdrawalRequest;
import com.ptutor.backend.entity.enums.UserStatus;
import com.ptutor.backend.entity.enums.WithdrawalRequestStatus;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.WithdrawalMapper;
import com.ptutor.backend.repository.BankAccountRepository;
import com.ptutor.backend.repository.WalletRepository;
import com.ptutor.backend.repository.WithdrawalRequestRepository;
import com.ptutor.backend.security.CurrentUserProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WithdrawalService {

    private final WalletRepository walletRepository;
    private final BankAccountRepository bankAccountRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final WithdrawalMapper withdrawalMapper;
    private final CurrentUserProvider currentUserProvider;

    @Transactional
    public WithdrawalResponse create(CreateWithdrawalRequest request, String rawKey) {
        String key = normalizeIdempotencyKey(rawKey);
        BigDecimal amount = normalize(request.amount());
        Wallet wallet = walletRepository.findByUserIdForUpdate(currentUserProvider.getCurrentUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", "Wallet not found"));

        ensureActiveUser(wallet);

        var existing = withdrawalRequestRepository.findByWallet_IdAndIdempotencyKey(wallet.getId(), key);
        if (existing.isPresent()) {
            return withdrawalMapper.toResponse(existing.get());
        }

        BankAccount bank = bankAccountRepository.findByUser_Id(wallet.getUser().getId())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        "BANK_ACCOUNT_REQUIRED",
                        "A bank account must be configured before requesting a withdrawal"));

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "INSUFFICIENT_WALLET_BALANCE",
                    "Wallet balance is insufficient");
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        wallet.setPendingBalance(wallet.getPendingBalance().add(amount));

        WithdrawalRequest saved = withdrawalRequestRepository.saveAndFlush(WithdrawalRequest.builder()
                .wallet(wallet)
                .amount(amount)
                .bankCode(bank.getBankCode())
                .bankName(bank.getBankName())
                .encryptedAccountNumber(bank.getEncryptedAccountNumber())
                .accountNumberLastFour(bank.getAccountNumberLastFour())
                .accountHolderName(bank.getAccountHolderName())
                .note(normalizeNote(request.note()))
                .idempotencyKey(key)
                .status(WithdrawalRequestStatus.PENDING)
                .build());

        return withdrawalMapper.toResponse(saved);
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

        if (wallet.getPendingBalance().compareTo(withdrawalRequest.getAmount()) < 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "INVALID_PENDING_WALLET_BALANCE",
                    "Wallet pending balance is inconsistent with the withdrawal request");
        }

        wallet.setPendingBalance(wallet.getPendingBalance().subtract(withdrawalRequest.getAmount()));
        wallet.setBalance(wallet.getBalance().add(withdrawalRequest.getAmount()));
        withdrawalRequest.setStatus(WithdrawalRequestStatus.CANCELLED);

        return withdrawalMapper.toResponse(withdrawalRequestRepository.saveAndFlush(withdrawalRequest));
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
            if (value == null || value.signum() <= 0) {
                throw new ArithmeticException();
            }
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_WALLET_AMOUNT",
                    "Amount must be positive with at most two decimal places");
        }
    }

    private String normalizeNote(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
