package com.autofolio.shared.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Testes Unitários - StrongPasswordValidator")
class StrongPasswordValidatorTest {

    private final StrongPasswordValidator validator = new StrongPasswordValidator();

    @ParameterizedTest
    @ValueSource(strings = {"Abcdefg1", "SenhaForte123", "Ação1234Ok", "aaaaaaaA1"})
    @DisplayName("Aceita senhas com 8+ caracteres, número e maiúscula")
    void shouldAcceptValid(String password) {
        assertThat(validator.isValid(password, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Abcde1", "abcdefg1", "ABCDEFGH", "Abcdefgh", "abcdefgh", "12345678", ""})
    @DisplayName("Rejeita senhas curtas ou sem número/maiúscula")
    void shouldRejectInvalid(String password) {
        assertThat(validator.isValid(password, null)).isFalse();
    }

    @Test
    @DisplayName("Limita a 72 bytes (limite do BCrypt)")
    void shouldRejectMoreThan72Bytes() {
        assertThat(validator.isValid("A1" + "a".repeat(70), null)).isTrue();
        assertThat(validator.isValid("A1" + "a".repeat(71), null)).isFalse();
        // 36 caracteres de 2 bytes (72) + "A1" ultrapassa o limite
        assertThat(validator.isValid("A1" + "é".repeat(36), null)).isFalse();
    }

    @Test
    @DisplayName("Nulo é delegado ao @NotBlank")
    void shouldDelegateNull() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
