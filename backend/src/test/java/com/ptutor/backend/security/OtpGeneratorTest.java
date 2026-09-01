package com.ptutor.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.security.SecureRandom;

import org.junit.jupiter.api.Test;

class OtpGeneratorTest {

    @Test
    void generateAlwaysReturnsSixDigitsIncludingLeadingZeros() {
        SecureRandom secureRandom = org.mockito.Mockito.mock(SecureRandom.class);
        when(secureRandom.nextInt(1_000_000)).thenReturn(42);

        String otp = new OtpGenerator(secureRandom).generate();

        assertThat(otp).isEqualTo("000042").matches("\\d{6}");
    }
}
