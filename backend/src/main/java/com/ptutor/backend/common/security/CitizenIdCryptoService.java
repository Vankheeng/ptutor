package com.ptutor.backend.common.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Encrypts citizen IDs at rest and creates a one-way blind index for lookups.
 * The master key must be supplied through the environment in production.
 */
@Service
public class CitizenIdCryptoService {

    private static final int AES_KEY_BYTES = 32;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String KEY_DERIVATION_ALGORITHM = "SHA-256";

    private final SecretKeySpec encryptionKey;
    private final SecretKeySpec hashKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public CitizenIdCryptoService(
            @Value("${app.security.citizen-id-encryption-key}") String encodedMasterKey) {
        byte[] masterKey;
        try {
            masterKey = Base64.getDecoder().decode(encodedMasterKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Citizen ID encryption key must be valid Base64", exception);
        }
        if (masterKey.length != AES_KEY_BYTES) {
            throw new IllegalStateException("Citizen ID encryption key must decode to exactly 32 bytes");
        }

        encryptionKey = new SecretKeySpec(deriveKey("encryption", masterKey), "AES");
        hashKey = new SecretKeySpec(deriveKey("blind-index", masterKey), HMAC_ALGORITHM);
    }

    public String encrypt(String citizenId) {
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(citizenId.getBytes(StandardCharsets.UTF_8));

            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(ByteBuffer.allocate(iv.length + ciphertext.length)
                            .put(iv)
                            .put(ciphertext)
                            .array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not encrypt citizen ID", exception);
        }
    }

    public String decrypt(String encryptedCitizenId) {
        try {
            byte[] payload = Base64.getUrlDecoder().decode(encryptedCitizenId);
            if (payload.length <= GCM_IV_BYTES) {
                throw new IllegalArgumentException("Encrypted citizen ID payload is invalid");
            }

            byte[] iv = new byte[GCM_IV_BYTES];
            byte[] ciphertext = new byte[payload.length - GCM_IV_BYTES];
            System.arraycopy(payload, 0, iv, 0, iv.length);
            System.arraycopy(payload, iv.length, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Could not decrypt citizen ID", exception);
        }
    }

    public String hash(String citizenId) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(hashKey);
            return HexFormat.of().formatHex(mac.doFinal(citizenId.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not create citizen ID blind index", exception);
        }
    }

    private byte[] deriveKey(String purpose, byte[] masterKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance(KEY_DERIVATION_ALGORITHM);
            digest.update(purpose.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            return digest.digest(masterKey);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required hash algorithm is unavailable", exception);
        }
    }
}
