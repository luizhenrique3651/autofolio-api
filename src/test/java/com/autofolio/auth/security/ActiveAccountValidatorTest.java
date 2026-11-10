package com.autofolio.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.autofolio.auth.repository.UserRepository;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
@DisplayName("Testes Unitários - ActiveAccountValidator")
class ActiveAccountValidatorTest {

    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private ActiveAccountValidator validator;

    private static Jwt jwt(String subject) {
        var builder = Jwt.withTokenValue("t").header("alg", "HS256").claim("x", "y");
        if (subject != null) {
            builder.subject(subject);
        }
        return builder.build();
    }

    @Test
    @DisplayName("Conta ativa: token válido")
    void shouldAcceptActiveAccount() {
        var id = UUID.randomUUID();
        when(userRepository.existsByIdAndActiveTrue(id)).thenReturn(true);

        assertThat(validator.validate(jwt(id.toString())).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("Conta inexistente ou desativada: falha invalid_token genérico e WARN com userId")
    void shouldRejectInactiveAccount(CapturedOutput output) {
        var id = UUID.randomUUID();
        when(userRepository.existsByIdAndActiveTrue(id)).thenReturn(false);

        var result = validator.validate(jwt(id.toString()));

        assertThat(result.hasErrors()).isTrue();
        var error = result.getErrors().iterator().next();
        assertThat(error.getErrorCode()).isEqualTo("invalid_token");
        assertThat(error.getDescription()).doesNotContainIgnoringCase("conta")
                .doesNotContainIgnoringCase("desativ");
        assertThat(output.getOut()).contains("userId=" + id);
    }

    @Test
    @DisplayName("sub ausente ou malformado: falha sem consultar o banco")
    void shouldRejectInvalidSubject() {
        assertThat(validator.validate(jwt(null)).hasErrors()).isTrue();
        assertThat(validator.validate(jwt("nao-e-uuid")).hasErrors()).isTrue();
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("Banco fora: lança AccountLookupUnavailableException; 1 ERROR sem throwable, com "
            + "userId e tipos; stacktrace só no DEBUG")
    void shouldSignalInfrastructureFailure() {
        var id = UUID.randomUUID();
        when(userRepository.existsByIdAndActiveTrue(id))
                .thenThrow(new DataAccessResourceFailureException("conexão recusada",
                        new SQLException("senha=segredo")));
        var logger = (Logger) LoggerFactory.getLogger(ActiveAccountValidator.class);
        var previousLevel = logger.getLevel();
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        try {
            assertThatThrownBy(() -> validator.validate(jwt(id.toString())))
                    .isInstanceOf(AccountLookupUnavailableException.class)
                    .hasCauseInstanceOf(DataAccessResourceFailureException.class);
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
        }

        var errors = appender.list.stream().filter(e -> e.getLevel() == Level.ERROR).toList();
        assertThat(errors).hasSize(1);
        var error = errors.getFirst();
        assertThat(error.getThrowableProxy()).isNull();
        assertThat(error.getFormattedMessage()).contains("userId=" + id)
                .contains("DataAccessResourceFailureException").contains("SQLException")
                .doesNotContain("conexão recusada").doesNotContain("segredo");
        var debugs = appender.list.stream().filter(e -> e.getLevel() == Level.DEBUG).toList();
        assertThat(debugs).hasSize(1);
        assertThat(debugs.getFirst().getThrowableProxy().getClassName())
                .isEqualTo(DataAccessResourceFailureException.class.getName());
    }
}
