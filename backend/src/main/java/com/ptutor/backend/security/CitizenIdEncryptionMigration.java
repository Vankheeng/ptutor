package com.ptutor.backend.security;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ptutor.backend.repository.UserRepository;
import com.ptutor.backend.entity.User;

import lombok.RequiredArgsConstructor;

/**
 * Migrates legacy plaintext citizen IDs after Flyway has changed the column.
 * The operation is idempotent and does not log the sensitive value.
 */
@Component
@RequiredArgsConstructor
public class CitizenIdEncryptionMigration implements ApplicationRunner {

    private static final String LEGACY_CITIZEN_ID_PATTERN = "\\d{12}";

    private final UserRepository userRepository;
    private final CitizenIdCryptoService citizenIdCryptoService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<User> users = userRepository.findAll();
        for (User user : users) {
            migrateUser(user);
        }
    }

    private void migrateUser(User user) {
        String storedValue = user.getEncryptedCitizenId();
        String citizenId;

        if (storedValue.matches(LEGACY_CITIZEN_ID_PATTERN)) {
            citizenId = storedValue;
            user.setEncryptedCitizenId(citizenIdCryptoService.encrypt(citizenId));
        } else {
            citizenId = citizenIdCryptoService.decrypt(storedValue);
        }

        String expectedHash = citizenIdCryptoService.hash(citizenId);
        if (!expectedHash.equals(user.getCitizenIdHash())) {
            user.setCitizenIdHash(expectedHash);
        }
    }
}
