package com.autofolio.shared.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Testes Unitários - TokenHasher")
class TokenHasherTest {

    @Test
    @DisplayName("SHA-256 hex determinístico (vetor conhecido) com 64 caracteres")
    void shouldHashDeterministically() {
        assertThat(TokenHasher.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(TokenHasher.sha256Hex("x")).hasSize(64).isEqualTo(TokenHasher.sha256Hex("x"));
    }
}
