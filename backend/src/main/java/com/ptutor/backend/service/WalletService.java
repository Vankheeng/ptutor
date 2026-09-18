package com.ptutor.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.command.WalletTransactionCommand;
import com.ptutor.backend.dto.response.PageResponse;
import com.ptutor.backend.dto.response.WalletBalanceResponse;
import com.ptutor.backend.dto.response.WalletTransactionResponse;
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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WalletService {

    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 100;

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final WalletMapper walletMapper;
    private final WalletTransactionMapper walletTransactionMapper;
    private final CurrentUserProvider currentUserProvider;

    @Transactional(readOnly = true)
    public WalletBalanceResponse getCurrentBalance() {
        return walletRepository.findByUser_Id(currentUserProvider.getCurrentUserId())
                .map(walletMapper::toBalanceResponse)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", "Wallet not found"));
    }

    @Transactional
    public WalletTransactionResponse credit(WalletTransactionCommand command) {
        Wallet wallet = findWalletForUpdate(command.userId());
        WalletTransaction transaction = applyCredit(wallet, command, WalletTransactionStatus.COMPLETED);
        return walletTransactionMapper.toResponse(transaction);
    }

    @Transactional
    public WalletTransactionResponse debit(WalletTransactionCommand command) {
        Wallet wallet = findWalletForUpdate(command.userId());
        WalletTransaction transaction = applyDebit(wallet, command, WalletTransactionStatus.COMPLETED);
        return walletTransactionMapper.toResponse(transaction);
    }

    @Transactional(readOnly = true)
    public PageResponse<WalletTransactionResponse> getMyTransactions(
            WalletTransactionType transactionType,
            WalletTransactionPurpose purpose,
            WalletTransactionStatus status,
            LocalDateTime createdFrom,
            LocalDateTime createdTo,
            int page,
            int size) {
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TRANSACTION_DATE_RANGE",
                    "createdFrom must not be after createdTo");
        }

        UUID userId = currentUserProvider.getCurrentUserId();
        Page<WalletTransaction> transactions = walletTransactionRepository.findAll(
                transactionSpecification(userId, transactionType, purpose, status, createdFrom, createdTo),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));

        return PageResponse.from(transactions,
                transactions.getContent().stream().map(walletTransactionMapper::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public WalletTransactionResponse getMyTransaction(UUID transactionId) {
        UUID userId = currentUserProvider.getCurrentUserId();
        return walletTransactionRepository.findById(transactionId)
                .filter(transaction -> Objects.equals(transaction.getWallet().getUser().getId(), userId))
                .map(walletTransactionMapper::toResponse)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WALLET_TRANSACTION_NOT_FOUND",
                        "Wallet transaction not found"));
    }

    /**
     * Called only by WithdrawalService after it has acquired the pessimistic wallet lock.
     */
    public WalletTransaction reserveForWithdrawal(
            Wallet wallet,
            UUID withdrawalRequestId,
            BigDecimal amount,
            String idempotencyKey,
            String description) {
        WalletTransactionCommand command = new WalletTransactionCommand(
                wallet.getUser().getId(),
                amount,
                WalletTransactionPurpose.WITHDRAWAL,
                ReferenceType.WITHDRAWAL_REQUEST,
                withdrawalRequestId,
                description,
                idempotencyKey);
        return applyDebit(wallet, command, WalletTransactionStatus.PENDING);
    }

    /**
     * Called only by WithdrawalService after it has acquired the pessimistic wallet lock.
     */
    public WalletTransaction reverseWithdrawal(Wallet wallet, WalletTransaction withdrawalTransaction, UUID withdrawalRequestId) {
        if (withdrawalTransaction.getStatus() != WalletTransactionStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "WITHDRAWAL_TRANSACTION_NOT_REVERSIBLE",
                    "Only a pending withdrawal transaction can be reversed");
        }
        if (wallet.getPendingBalance().compareTo(withdrawalTransaction.getAmount()) < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_PENDING_WALLET_BALANCE",
                    "Wallet pending balance is inconsistent with the withdrawal transaction");
        }

        withdrawalTransaction.setStatus(WalletTransactionStatus.CANCELLED);
        walletTransactionRepository.saveAndFlush(withdrawalTransaction);

        WalletTransactionCommand reversal = new WalletTransactionCommand(
                wallet.getUser().getId(),
                withdrawalTransaction.getAmount(),
                WalletTransactionPurpose.WITHDRAWAL_REVERSAL,
                ReferenceType.WITHDRAWAL_REQUEST,
                withdrawalRequestId,
                "Withdrawal request cancelled",
                "withdrawal-reversal-" + withdrawalRequestId);
        return applyCreditFromPending(wallet, reversal);
    }

    private Wallet findWalletForUpdate(UUID userId) {
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", "Wallet not found"));
        if (wallet.getUser().getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "USER_INACTIVE", "User account is not active");
        }
        return wallet;
    }

    private WalletTransaction applyCredit(
            Wallet wallet,
            WalletTransactionCommand command,
            WalletTransactionStatus status) {
        validateCommand(command, WalletTransactionType.CREDIT);
        WalletTransaction existing = findExisting(wallet, command.idempotencyKey());
        if (existing != null) {
            return existing;
        }

        BigDecimal amount = normalizeAmount(command.amount());
        wallet.setBalance(wallet.getBalance().add(amount));
        return saveTransaction(wallet, command, WalletTransactionType.CREDIT, amount, status);
    }

    private WalletTransaction applyCreditFromPending(Wallet wallet, WalletTransactionCommand command) {
        validateCommand(command, WalletTransactionType.CREDIT);
        WalletTransaction existing = findExisting(wallet, command.idempotencyKey());
        if (existing != null) {
            return existing;
        }

        BigDecimal amount = normalizeAmount(command.amount());
        wallet.setPendingBalance(wallet.getPendingBalance().subtract(amount));
        wallet.setBalance(wallet.getBalance().add(amount));
        return saveTransaction(wallet, command, WalletTransactionType.CREDIT, amount, WalletTransactionStatus.COMPLETED);
    }

    private WalletTransaction applyDebit(
            Wallet wallet,
            WalletTransactionCommand command,
            WalletTransactionStatus status) {
        validateCommand(command, WalletTransactionType.DEBIT);
        WalletTransaction existing = findExisting(wallet, command.idempotencyKey());
        if (existing != null) {
            return existing;
        }

        BigDecimal amount = normalizeAmount(command.amount());
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_WALLET_BALANCE", "Wallet balance is insufficient");
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        if (command.purpose() == WalletTransactionPurpose.WITHDRAWAL) {
            wallet.setPendingBalance(wallet.getPendingBalance().add(amount));
        }
        return saveTransaction(wallet, command, WalletTransactionType.DEBIT, amount, status);
    }

    private WalletTransaction saveTransaction(
            Wallet wallet,
            WalletTransactionCommand command,
            WalletTransactionType transactionType,
            BigDecimal amount,
            WalletTransactionStatus status) {
        return walletTransactionRepository.saveAndFlush(WalletTransaction.builder()
                .wallet(wallet)
                .transactionType(transactionType)
                .purpose(command.purpose())
                .amount(amount)
                .balanceAfter(wallet.getBalance())
                .pendingBalanceAfter(wallet.getPendingBalance())
                .referenceType(command.referenceType())
                .referenceId(command.referenceId())
                .description(normalizeDescription(command.description()))
                .idempotencyKey(normalizeIdempotencyKey(command.idempotencyKey()))
                .status(status)
                .build());
    }

    private WalletTransaction findExisting(Wallet wallet, String idempotencyKey) {
        return walletTransactionRepository.findByWallet_IdAndIdempotencyKey(
                wallet.getId(), normalizeIdempotencyKey(idempotencyKey)).orElse(null);
    }

    private void validateCommand(WalletTransactionCommand command, WalletTransactionType expectedType) {
        if (command == null || command.userId() == null || command.purpose() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WALLET_TRANSACTION", "Wallet transaction is incomplete");
        }
        if (expectedType == WalletTransactionType.CREDIT && !isCreditPurpose(command.purpose())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WALLET_TRANSACTION_PURPOSE",
                    "Purpose is not valid for a credit transaction");
        }
        if (expectedType == WalletTransactionType.DEBIT && !isDebitPurpose(command.purpose())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WALLET_TRANSACTION_PURPOSE",
                    "Purpose is not valid for a debit transaction");
        }
        normalizeAmount(command.amount());
        normalizeIdempotencyKey(command.idempotencyKey());
    }

    private boolean isCreditPurpose(WalletTransactionPurpose purpose) {
        return switch (purpose) {
            case TOP_UP, TUTOR_EARNING, CONTRACT_REFUND, WITHDRAWAL_REVERSAL -> true;
            default -> false;
        };
    }

    private boolean isDebitPurpose(WalletTransactionPurpose purpose) {
        return switch (purpose) {
            case TUITION_PAYMENT, STUDYING_REQUEST_FEE, TEACHING_REQUEST_FEE, WITHDRAWAL -> true;
            default -> false;
        };
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        try {
            if (amount == null || amount.signum() <= 0) {
                throw new ArithmeticException();
            }
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WALLET_AMOUNT",
                    "Amount must be positive with at most two decimal places");
        }
    }

    private String normalizeIdempotencyKey(String value) {
        String key = value == null ? "" : value.strip();
        if (key.isEmpty() || key.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY",
                    "Idempotency key must contain 1 to 100 characters");
        }
        return key;
    }

    private String normalizeDescription(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private Specification<WalletTransaction> transactionSpecification(
            UUID userId,
            WalletTransactionType transactionType,
            WalletTransactionPurpose purpose,
            WalletTransactionStatus status,
            LocalDateTime createdFrom,
            LocalDateTime createdTo) {
        return (root, query, builder) -> {
            var predicate = builder.equal(root.get("wallet").get("user").get("id"), userId);
            if (transactionType != null) {
                predicate = builder.and(predicate, builder.equal(root.get("transactionType"), transactionType));
            }
            if (purpose != null) {
                predicate = builder.and(predicate, builder.equal(root.get("purpose"), purpose));
            }
            if (status != null) {
                predicate = builder.and(predicate, builder.equal(root.get("status"), status));
            }
            if (createdFrom != null) {
                predicate = builder.and(predicate, builder.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
            }
            if (createdTo != null) {
                predicate = builder.and(predicate, builder.lessThanOrEqualTo(root.get("createdAt"), createdTo));
            }
            return predicate;
        };
    }
}
