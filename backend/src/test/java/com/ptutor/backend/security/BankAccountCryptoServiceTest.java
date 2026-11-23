package com.ptutor.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.Test;

class BankAccountCryptoServiceTest {

    @Test
    void decryptsOnlyAuthenticAccountNumbers() {
        BankAccountCryptoService service = new BankAccountCryptoService(
                Base64.getEncoder().encodeToString(new byte[32]));
        String encrypted = service.encrypt("0123456789");
        assertThat(service.decrypt(encrypted)).isEqualTo("0123456789");

        byte[] tampered = Base64.getUrlDecoder().decode(encrypted);
        tampered[tampered.length - 1] ^= 1;
        String modified = Base64.getUrlEncoder().withoutPadding().encodeToString(tampered);
        assertThatThrownBy(() -> service.decrypt(modified)).isInstanceOf(IllegalStateException.class);
    }
}
