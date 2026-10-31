package com.autofolio.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Testes Unitários - JwtConfig")
class JwtConfigTest {

    @Test
    @DisplayName("Deve rejeitar secret com menos de 32 bytes")
    void shouldRejectShortSecret() {
        assertThatThrownBy(() -> new JwtConfig("a".repeat(31)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32").hasMessageContaining("bytes");
    }

    @Test
    @DisplayName("Deve aceitar secret com exatamente 32 bytes")
    void shouldAcceptSecretWith32Bytes() {
        var config = new JwtConfig("a".repeat(32));

        assertThat(config.jwtEncoder()).isNotNull();
        assertThat(config.jwtDecoder()).isNotNull();
    }

    @Test
    @DisplayName("Deve contar bytes e não caracteres: 16 'é' (32 bytes) aceito")
    void shouldAcceptMultibyteSecretWith32Bytes() {
        assertThat(new JwtConfig("é".repeat(16)).jwtEncoder()).isNotNull();
    }

    @Test
    @DisplayName("Deve contar bytes e não caracteres: 15 'é' + 'a' (31 bytes) rejeitado")
    void shouldRejectMultibyteSecretWith31Bytes() {
        assertThatThrownBy(() -> new JwtConfig("é".repeat(15) + "a"))
                .isInstanceOf(IllegalStateException.class);
    }
}
