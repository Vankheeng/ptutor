package com.ptutor.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CitizenIdCryptoServiceTest {

    private static final String KEY = "cHR1dG9yLWxvY2FsLWNpdGl6ZW4taWQta2V5LTMyISE=";

    private final CitizenIdCryptoService cryptoService = new CitizenIdCryptoService(KEY);

    @Test
    void encryptsAndDecryptsCitizenId() {
        String encrypted = cryptoService.encrypt("012345678901");

        assertThat(encrypted).isNotEqualTo("012345678901");
        assertThat(cryptoService.decrypt(encrypted)).isEqualTo("012345678901");
    }

    @Test
    void usesRandomIvAndStableBlindIndex() {
        String first = cryptoService.encrypt("012345678901");
        String second = cryptoService.encrypt("012345678901");

        assertThat(first).isNotEqualTo(second);
        assertThat(cryptoService.hash("012345678901"))
                .isEqualTo(cryptoService.hash("012345678901"))
                .hasSize(64);
    }
}
