package com.autofolio.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.autofolio.shared.exception.InvalidSessionException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Testes Unitários - CurrentUser")
class CurrentUserTest {

    private static Jwt jwt(String subject) {
        var builder = Jwt.withTokenValue("t").header("alg", "HS256").claim("x", "y");
        if (subject != null) {
            builder.subject(subject);
        }
        return builder.build();
    }

    @Test
    @DisplayName("Extrai o UUID do sub")
    void shouldExtractId() {
        var id = UUID.randomUUID();
        assertThat(CurrentUser.id(jwt(id.toString()))).isEqualTo(id);
    }

    @Test
    @DisplayName("sub ausente ou malformado lança InvalidSessionException")
    void shouldRejectInvalidSubject(CapturedOutput output) {
        assertThatThrownBy(() -> CurrentUser.id(jwt(null)))
                .isInstanceOf(InvalidSessionException.class);
        assertThatThrownBy(() -> CurrentUser.id(jwt("nao-e-uuid")))
                .isInstanceOf(InvalidSessionException.class);

        assertThat(output.getOut()).contains("JWT recusado").doesNotContain("nao-e-uuid");
    }
}
