package com.ptutor.backend.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Encrypts bank account numbers at rest with a key distinct from citizen IDs. */
@Service
public class BankAccountCryptoService {

    private static final int AES_KEY_BYTES = 32;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";

    private final SecretKeySpec encryptionKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public BankAccountCryptoService(
            @Value("${app.security.bank-account-encryption-key:${app.security.citizen-id-encryption-key}}") String encodedMasterKey) {
        byte[] masterKey;
        try {
            masterKey = Base64.getDecoder().decode(encodedMasterKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Bank account encryption key must be valid Base64", exception);
        }
        if (masterKey.length != AES_KEY_BYTES) {
            throw new IllegalStateException("Bank account encryption key must decode to exactly 32 bytes");
        }
        encryptionKey = new SecretKeySpec(deriveKey(masterKey), "AES");
    }

    public String encrypt(String accountNumber) {
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(accountNumber.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not encrypt bank account number", exception);
        }
    }

    private byte[] deriveKey(byte[] masterKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("bank-account-number".getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            return digest.digest(masterKey);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required hash algorithm is unavailable", exception);
        }
    }
}
