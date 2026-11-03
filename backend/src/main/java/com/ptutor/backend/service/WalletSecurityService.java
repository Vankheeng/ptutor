package com.ptutor.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.request.ConfirmWalletSecurityChallengeRequest;
import com.ptutor.backend.dto.request.CreateWithdrawalRequest;
import com.ptutor.backend.dto.request.RequestWithdrawalOtpRequest;
import com.ptutor.backend.dto.request.UpdateBankAccountRequest;
import com.ptutor.backend.dto.response.BankAccountResponse;
import com.ptutor.backend.dto.response.WalletSecurityChallengeResponse;
import com.ptutor.backend.dto.response.WithdrawalResponse;
import com.ptutor.backend.entity.BankAccount;
import com.ptutor.backend.entity.Wallet;
import com.ptutor.backend.entity.WalletSecurityChallenge;
import com.ptutor.backend.entity.enums.UserStatus;
import com.ptutor.backend.entity.enums.WalletSecurityChallengePurpose;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.BankAccountMapper;
import com.ptutor.backend.repository.BankAccountRepository;
import com.ptutor.backend.repository.WalletRepository;
import com.ptutor.backend.repository.WalletSecurityChallengeRepository;
import com.ptutor.backend.security.BankAccountCryptoService;
import com.ptutor.backend.security.CurrentUserProvider;
import com.ptutor.backend.security.OtpGenerator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WalletSecurityService {

    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final int MAX_ATTEMPTS = 5;

    private final WalletSecurityChallengeRepository challengeRepository;
    private final WalletRepository walletRepository;
    private final BankAccountRepository bankAccountRepository;
    private final CurrentUserProvider currentUserProvider;
    private final PasswordEncoder passwordEncoder;
    private final OtpGenerator otpGenerator;
    private final EmailService emailService;
    private final Clock clock;
    private final BankAccountCryptoService bankAccountCryptoService;
    private final BankAccountMapper bankAccountMapper;
    private final WithdrawalService withdrawalService;

    @Transactional
    public WalletSecurityChallengeResponse requestWithdrawalOtp(RequestWithdrawalOtpRequest request, String rawKey) {
        UUID userId = currentUserProvider.getCurrentUserId();
        Wallet wallet = activeWallet(userId);
        String key = normalizeKey(rawKey);
        BigDecimal amount = normalizeAmount(request.amount());
        BankAccount bank = bankAccountRepository.findByUser_Id(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "BANK_ACCOUNT_REQUIRED",
                        "A bank account must be configured before requesting a withdrawal"));

        var existingChallenge = challengeRepository
                .findFirstByUser_IdAndPurposeAndIdempotencyKeyAndConsumedAtIsNullAndInvalidatedAtIsNullOrderByCreatedAtDesc(
                        userId, WalletSecurityChallengePurpose.WITHDRAWAL, key)
                .filter(value -> value.getExpiresAt().isAfter(now()));
        if (existingChallenge.isPresent()) {
            WalletSecurityChallenge prior = existingChallenge.get();
            if (prior.getAmount().compareTo(amount) != 0
                    || !java.util.Objects.equals(prior.getNote(), normalizeNote(request.note()))
                    || !sameBank(prior, bank)) {
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                        "Idempotency-Key was already used for a different withdrawal request");
            }
            return toResponse(prior);
        }

        IssuedChallenge issued = issue(userId, WalletSecurityChallengePurpose.WITHDRAWAL);
        WalletSecurityChallenge challenge = issued.challenge();
        challenge.setAmount(amount);
        challenge.setNote(normalizeNote(request.note()));
        challenge.setBankCode(bank.getBankCode());
        challenge.setBankName(bank.getBankName());
        challenge.setEncryptedAccountNumber(bank.getEncryptedAccountNumber());
        challenge.setAccountNumberLastFour(bank.getAccountNumberLastFour());
        challenge.setAccountHolderName(bank.getAccountHolderName());
        challenge.setIdempotencyKey(key);
        challengeRepository.saveAndFlush(challenge);
        emailService.sendWithdrawalOtp(wallet.getUser().getEmail(), issued.rawOtp(),
                amount.stripTrailingZeros().toPlainString(), masked(challenge.getAccountNumberLastFour()));
        return toResponse(challenge);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public WithdrawalResponse confirmWithdrawal(ConfirmWalletSecurityChallengeRequest request, String rawKey) {
        UUID userId = currentUserProvider.getCurrentUserId();
        String key = normalizeKey(rawKey);
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", "Wallet not found"));
        WalletSecurityChallenge challenge = challengeRepository.findByIdAndUserIdForUpdate(request.challengeId(), userId)
                .orElseThrow(WalletSecurityService::invalidOtp);

        if (challenge.getPurpose() != WalletSecurityChallengePurpose.WITHDRAWAL
                || !key.equals(challenge.getIdempotencyKey())) {
            throw invalidOtp();
        }
        if (challenge.getConsumedAt() != null && challenge.getWithdrawalRequest() != null) {
            return withdrawalService.getMine(challenge.getWithdrawalRequest().getId());
        }
        validateOtp(challenge, request.otp());
        ensureActive(wallet);
        BankAccount currentBank = bankAccountRepository.findByUser_Id(userId)
                .orElseThrow(() -> invalidOtp());
        if (!sameBank(challenge, currentBank)) {
            challenge.setInvalidatedAt(now());
            throw invalidOtp();
        }

        CreateWithdrawalRequest withdrawal = new CreateWithdrawalRequest(challenge.getAmount(), challenge.getNote());
        WithdrawalResponse response = withdrawalService.createVerified(
                withdrawal, key, wallet, challenge);
        challenge.setConsumedAt(now());
        return response;
    }

    @Transactional
    public WalletSecurityChallengeResponse requestBankAccountOtp(UpdateBankAccountRequest request) {
        UUID userId = currentUserProvider.getCurrentUserId();
        Wallet wallet = activeWallet(userId);
        String accountNumber = request.accountNumber().strip();
        IssuedChallenge issued = issue(userId, WalletSecurityChallengePurpose.BANK_ACCOUNT_UPDATE);
        WalletSecurityChallenge challenge = issued.challenge();
        challenge.setBankCode(request.bankCode().strip().toUpperCase(java.util.Locale.ROOT));
        challenge.setBankName(normalizeSpaces(request.bankName()));
        challenge.setEncryptedAccountNumber(bankAccountCryptoService.encrypt(accountNumber));
        challenge.setAccountNumberLastFour(accountNumber.substring(accountNumber.length() - 4));
        challenge.setAccountHolderName(normalizeSpaces(request.accountHolderName()));
        challengeRepository.saveAndFlush(challenge);
        emailService.sendBankAccountUpdateOtp(wallet.getUser().getEmail(), issued.rawOtp(),
                challenge.getBankName(), masked(challenge.getAccountNumberLastFour()));
        return toResponse(challenge);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public BankAccountResponse confirmBankAccount(ConfirmWalletSecurityChallengeRequest request) {
        UUID userId = currentUserProvider.getCurrentUserId();
        Wallet wallet = activeWallet(userId);
        WalletSecurityChallenge challenge = challengeRepository.findByIdAndUserIdForUpdate(request.challengeId(), userId)
                .orElseThrow(WalletSecurityService::invalidOtp);
        if (challenge.getPurpose() != WalletSecurityChallengePurpose.BANK_ACCOUNT_UPDATE) {
            throw invalidOtp();
        }
        validateOtp(challenge, request.otp());
        BankAccount account = bankAccountRepository.findByUser_Id(userId)
                .orElseGet(() -> BankAccount.builder().user(wallet.getUser()).build());
        account.setBankCode(challenge.getBankCode());
        account.setBankName(challenge.getBankName());
        account.setEncryptedAccountNumber(challenge.getEncryptedAccountNumber());
        account.setAccountNumberLastFour(challenge.getAccountNumberLastFour());
        account.setAccountHolderName(challenge.getAccountHolderName());
        challenge.setConsumedAt(now());
        return bankAccountMapper.toResponse(bankAccountRepository.saveAndFlush(account));
    }

    private IssuedChallenge issue(UUID userId, WalletSecurityChallengePurpose purpose) {
        LocalDateTime now = now();
        var active = challengeRepository.findActiveForUpdate(userId, purpose);
        active.forEach(challenge -> challenge.setInvalidatedAt(now));
        String rawOtp = otpGenerator.generate();
        WalletSecurityChallenge challenge = WalletSecurityChallenge.builder()
                .user(walletRepository.findByUser_Id(userId).orElseThrow().getUser())
                .purpose(purpose)
                .otpHash(passwordEncoder.encode(rawOtp))
                .expiresAt(now.plus(OTP_TTL))
                .failedAttempts(0)
                .build();
        return new IssuedChallenge(challenge, rawOtp);
    }

    private void validateOtp(WalletSecurityChallenge challenge, String otp) {
        LocalDateTime now = now();
        if (challenge.getFailedAttempts() >= MAX_ATTEMPTS) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_ATTEMPTS_EXCEEDED",
                    "Bạn đã nhập sai mã xác minh quá nhiều lần. Vui lòng yêu cầu mã mới sau.");
        }
        if (challenge.getConsumedAt() != null || challenge.getInvalidatedAt() != null
                || !challenge.getExpiresAt().isAfter(now)) {
            throw invalidOtp();
        }
        if (!passwordEncoder.matches(otp, challenge.getOtpHash())) {
            challenge.setFailedAttempts(challenge.getFailedAttempts() + 1);
            throw invalidOtp();
        }
    }

    private Wallet activeWallet(UUID userId) {
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", "Wallet not found"));
        ensureActive(wallet);
        return wallet;
    }

    private void ensureActive(Wallet wallet) {
        if (wallet.getUser().getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "USER_INACTIVE", "User account is not active");
        }
    }

    private boolean sameBank(WalletSecurityChallenge challenge, BankAccount account) {
        return challenge.getBankCode().equals(account.getBankCode())
                && challenge.getBankName().equals(account.getBankName())
                && challenge.getEncryptedAccountNumber().equals(account.getEncryptedAccountNumber())
                && challenge.getAccountHolderName().equals(account.getAccountHolderName());
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        try {
            if (amount == null || amount.signum() <= 0) throw new ArithmeticException();
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WALLET_AMOUNT",
                    "Amount must be positive with at most two decimal places");
        }
    }

    private String normalizeKey(String key) {
        String value = key == null ? "" : key.strip();
        if (value.isEmpty() || value.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key must contain 1 to 100 characters");
        }
        return value;
    }

    private String normalizeNote(String note) { return note == null || note.isBlank() ? null : note.strip(); }
    private String normalizeSpaces(String value) { return value.strip().replaceAll("\\s+", " "); }
    private String masked(String lastFour) { return "****" + lastFour; }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private WalletSecurityChallengeResponse toResponse(WalletSecurityChallenge c) {
        return new WalletSecurityChallengeResponse(c.getId(), masked(c.getAccountNumberLastFour()), c.getExpiresAt());
    }
    private static ApiException invalidOtp() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_OR_EXPIRED_OTP",
                "Mã xác minh không hợp lệ, đã hết hạn hoặc đã được sử dụng.");
    }

    private record IssuedChallenge(WalletSecurityChallenge challenge, String rawOtp) {}
}
