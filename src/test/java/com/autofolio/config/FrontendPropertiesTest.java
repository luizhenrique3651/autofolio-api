package com.autofolio.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Testes Unitários - FrontendProperties")
class FrontendPropertiesTest {

    @Test
    @DisplayName("Remove barras finais da URL")
    void shouldStripTrailingSlashes() {
        assertThat(new FrontendProperties("https://app.exemplo.com//").url()).isEqualTo("https://app.exemplo.com");
        assertThat(new FrontendProperties("http://localhost:5173").url()).isEqualTo("http://localhost:5173");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    @DisplayName("URL nula, vazia ou em branco falha com mensagem que nomeia a propriedade")
    void shouldFailWithClearMessageWhenMissing(String url) {
        assertThatThrownBy(() -> new FrontendProperties(url))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("frontend.url é obrigatório");
    }
}
