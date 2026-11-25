package com.ptutor.backend.security;

import java.security.SecureRandom;
import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class OtpGenerator {

    private static final int OTP_BOUND = 1_000_000;

    private final SecureRandom secureRandom;

    public OtpGenerator() {
        this(new SecureRandom());
    }

    OtpGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public String generate() {
        return String.format(Locale.ROOT, "%06d", secureRandom.nextInt(OTP_BOUND));
    }
}
