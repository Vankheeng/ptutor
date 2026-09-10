package com.ptutor.backend.service;

import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.dto.request.UpdateBankAccountRequest;
import com.ptutor.backend.dto.response.BankAccountResponse;
import com.ptutor.backend.entity.BankAccount;
import com.ptutor.backend.entity.Wallet;
import com.ptutor.backend.entity.enums.UserStatus;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.mapper.BankAccountMapper;
import com.ptutor.backend.repository.BankAccountRepository;
import com.ptutor.backend.repository.WalletRepository;
import com.ptutor.backend.security.BankAccountCryptoService;
import com.ptutor.backend.security.CurrentUserProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BankAccountService {

    private final BankAccountRepository bankAccountRepository;
    private final WalletRepository walletRepository;
    private final BankAccountMapper bankAccountMapper;
    private final BankAccountCryptoService bankAccountCryptoService;
    private final CurrentUserProvider currentUserProvider;

    @Transactional(readOnly = true)
    public BankAccountResponse getCurrentBankAccount() {
        UUID userId = currentUserProvider.getCurrentUserId();
        return bankAccountRepository.findByUser_Id(userId)
                .map(bankAccountMapper::toResponse)
                .orElse(null);
    }

    @Transactional
    public BankAccountResponse updateCurrentBankAccount(UpdateBankAccountRequest request) {
        UUID userId = currentUserProvider.getCurrentUserId();
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", "Wallet not found"));
        if (wallet.getUser().getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "USER_INACTIVE", "User account is not active");
        }

        BankAccount bankAccount = bankAccountRepository.findByUser_Id(userId)
                .orElseGet(() -> BankAccount.builder().user(wallet.getUser()).build());
        String accountNumber = request.accountNumber().strip();
        bankAccount.setBankCode(request.bankCode().strip().toUpperCase(Locale.ROOT));
        bankAccount.setBankName(normalizeSpaces(request.bankName()));
        bankAccount.setEncryptedAccountNumber(bankAccountCryptoService.encrypt(accountNumber));
        bankAccount.setAccountNumberLastFour(accountNumber.substring(accountNumber.length() - 4));
        bankAccount.setAccountHolderName(normalizeSpaces(request.accountHolderName()));

        // Flush before mapping so Hibernate's @CreationTimestamp/@UpdateTimestamp
        // are available in the response for both first-time creation and updates.
        return bankAccountMapper.toResponse(bankAccountRepository.saveAndFlush(bankAccount));
    }

    private String normalizeSpaces(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }
}
