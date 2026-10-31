package com.autofolio.auth;

import com.autofolio.auth.domain.PasswordResetToken;
import com.autofolio.auth.domain.User;
import com.autofolio.auth.repository.PasswordResetTokenRepository;
import com.autofolio.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;

import java.net.ConnectException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
@DisplayName("Testes Unitários - PasswordResetService")
class PasswordResetServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordResetTokenRepository tokenRepository;
    @Mock
    private PasswordResetMailer mailer;

    private PasswordResetService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(userRepository, tokenRepository, mailer, Clock.fixed(NOW, ZoneOffset.UTC));
        user = User.builder().id(UUID.randomUUID()).name("Luiz").email("luiz@teste.com").build();
    }

    @Test
    @DisplayName("Deve gravar token UUID com expiração em 1h e enviar o e-mail")
    void shouldCreateTokenAndSendMail() {
        when(userRepository.findByEmail("luiz@teste.com")).thenReturn(Optional.of(user));

        service.requestReset("luiz@teste.com");

        var captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(captor.capture());
        var saved = captor.getValue();
        assertThat(UUID.fromString(saved.getToken())).isNotNull();
        assertThat(saved.isUsed()).isFalse();
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getExpiresAt()).isEqualTo(OffsetDateTime.ofInstant(NOW.plusSeconds(3600), ZoneOffset.UTC));
        verify(mailer).send("luiz@teste.com", "Luiz", saved.getToken(), PasswordResetService.TOKEN_TTL);
    }

    @Test
    @DisplayName("Deve ignorar silenciosamente e-mail inexistente")
    void shouldDoNothingWhenUnknownEmail() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThatCode(() -> service.requestReset("x@teste.com")).doesNotThrowAnyException();

        verifyNoInteractions(tokenRepository, mailer);
    }

    @Test
    @DisplayName("Não deve propagar falha de SMTP")
    void shouldSwallowMailFailure() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(user));
        doThrow(new MailSendException("smtp down")).when(mailer).send(anyString(), anyString(), anyString(), any());

        assertThatCode(() -> service.requestReset("luiz@teste.com")).doesNotThrowAnyException();

        verify(tokenRepository).save(any());
    }

    @Test
    @DisplayName("Não deve propagar falha de banco e não deve enviar e-mail")
    void shouldSwallowRepositoryFailure() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(user));
        when(tokenRepository.save(any())).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> service.requestReset("luiz@teste.com")).doesNotThrowAnyException();

        verify(mailer, never()).send(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Falha de e-mail: ERROR com userId e causa raiz, sem mensagem (PII)")
    void shouldLogMailFailureAsErrorWithRootCauseWithoutPii(CapturedOutput output) {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(user));
        var cause = new ConnectException("recusada para luiz@teste.com");
        doThrow(new MailSendException("falha para luiz@teste.com", cause))
                .when(mailer).send(anyString(), anyString(), anyString(), any());

        service.requestReset("luiz@teste.com");

        // O DEBUG com a stacktrace depende do nível configurado; o contrato é a linha ERROR sem PII
        var errorLine = output.getOut().lines().filter(l -> l.contains(" ERROR ")).findFirst().orElseThrow();
        assertThat(errorLine)
                .contains("userId=" + user.getId())
                .contains("MailSendException")
                .contains("causa raiz: ConnectException")
                .doesNotContain("luiz@teste.com");
    }

    @Test
    @DisplayName("Falha não relacionada a e-mail: ERROR com userId e stacktrace completa")
    void shouldLogOtherFailureAsErrorWithStacktrace(CapturedOutput output) {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(user));
        when(tokenRepository.save(any())).thenThrow(new IllegalStateException("db down"));

        service.requestReset("luiz@teste.com");

        assertThat(output.getOut())
                .contains("ERROR")
                .contains("userId=" + user.getId())
                .contains("IllegalStateException: db down")
                .contains("\tat ")
                .doesNotContain("luiz@teste.com");
    }
}
