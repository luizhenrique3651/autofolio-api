package com.autofolio.auth;

import com.autofolio.auth.domain.PasswordResetToken;
import com.autofolio.auth.domain.User;
import com.autofolio.auth.repository.PasswordResetTokenRepository;
import com.autofolio.auth.repository.RefreshTokenRepository;
import com.autofolio.auth.repository.UserRepository;
import com.autofolio.shared.exception.InvalidResetTokenException;
import com.autofolio.shared.security.TokenHasher;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.ConnectException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PasswordResetMailer mailer;

    private PasswordResetService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(userRepository, tokenRepository, refreshTokenRepository, passwordEncoder, mailer,
                Clock.fixed(NOW, ZoneOffset.UTC));
        user = User.builder().id(UUID.randomUUID()).name("Luiz").email("luiz@teste.com")
                .password("hash").slug("luiz-dev").build();
    }

    @Test
    @DisplayName("Deve gravar token UUID com expiração em 1h e enviar o e-mail")
    void shouldCreateTokenAndSendMail() {
        when(userRepository.findByEmail("luiz@teste.com")).thenReturn(Optional.of(user));

        service.requestReset("luiz@teste.com");

        var captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(captor.capture());
        var saved = captor.getValue();
        var rawToken = ArgumentCaptor.forClass(String.class);
        verify(mailer).send(eq("luiz@teste.com"), eq("Luiz"), rawToken.capture(), eq(PasswordResetService.TOKEN_TTL));
        assertThat(UUID.fromString(rawToken.getValue())).isNotNull();
        assertThat(saved.getTokenHash()).isEqualTo(TokenHasher.sha256Hex(rawToken.getValue())).isNotEqualTo(rawToken.getValue());
        assertThat(saved.isUsed()).isFalse();
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getExpiresAt()).isEqualTo(OffsetDateTime.ofInstant(NOW.plusSeconds(3600), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Novo pedido limpa tokens expirados/usados e invalida os anteriores do usuário")
    void shouldCleanUpAndInvalidatePreviousTokens() {
        when(userRepository.findByEmail("luiz@teste.com")).thenReturn(Optional.of(user));

        service.requestReset("luiz@teste.com");

        var order = org.mockito.Mockito.inOrder(tokenRepository);
        order.verify(tokenRepository).deleteExpiredOrUsedByUserId(user.getId(), OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        order.verify(tokenRepository).invalidateUnusedByUserId(user.getId());
        order.verify(tokenRepository).save(any(PasswordResetToken.class));
    }

    @Test
    @DisplayName("Falha na limpeza loga WARN e não impede gerar o token nem enviar o e-mail")
    void shouldStillCreateTokenWhenCleanupFails(CapturedOutput output) {
        when(userRepository.findByEmail("luiz@teste.com")).thenReturn(Optional.of(user));
        doThrow(new IllegalStateException("db")).when(tokenRepository).deleteExpiredOrUsedByUserId(any(), any());

        service.requestReset("luiz@teste.com");

        verify(tokenRepository).save(any(PasswordResetToken.class));
        verify(mailer).send(eq("luiz@teste.com"), eq("Luiz"), anyString(), any());
        assertThat(output.getOut()).contains("WARN").contains("Falha na limpeza").contains("userId=" + user.getId())
                .contains("IllegalStateException");
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

    @Test
    @DisplayName("Reset: consome o token pelo hash, grava a senha codificada e revoga os refresh tokens")
    void shouldResetPassword(CapturedOutput output) {
        var tokenHash = TokenHasher.sha256Hex("raw-token");
        var resetToken = PasswordResetToken.builder().user(user).tokenHash(tokenHash)
                .expiresAt(OffsetDateTime.ofInstant(NOW.plusSeconds(3600), ZoneOffset.UTC)).used(true).build();
        when(tokenRepository.consume(tokenHash, OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC))).thenReturn(1);
        when(tokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode("NovaSenha1")).thenReturn("hash-novo");
        when(refreshTokenRepository.revokeAllByUserId(user.getId())).thenReturn(2);

        service.resetPassword("raw-token", "NovaSenha1");

        assertThat(user.getPassword()).isEqualTo("hash-novo");
        verify(tokenRepository).invalidateUnusedByUserId(user.getId());
        verify(refreshTokenRepository).revokeAllByUserId(user.getId());
        assertThat(output.getOut()).contains("userId=" + user.getId())
                .doesNotContain("raw-token").doesNotContain("NovaSenha1").doesNotContain(tokenHash);
    }

    @Test
    @DisplayName("Reset: token inexistente, expirado ou usado (consume = 0) lança exceção única e não altera nada")
    void shouldRejectInvalidToken() {
        when(tokenRepository.consume(anyString(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.resetPassword("raw-token", "NovaSenha1"))
                .isInstanceOf(InvalidResetTokenException.class);

        verifyNoInteractions(userRepository, refreshTokenRepository, passwordEncoder);
    }

    @Test
    @DisplayName("Reset: WARN do token recusado traz só o prefixo do hash, nunca o token cru")
    void shouldLogHashPrefixOnRejectedToken(CapturedOutput output) {
        when(tokenRepository.consume(anyString(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.resetPassword("raw-token", "NovaSenha1"))
                .isInstanceOf(InvalidResetTokenException.class);

        var hash = TokenHasher.sha256Hex("raw-token");
        assertThat(output.getOut()).contains("tokenHashPrefix=" + hash.substring(0, 8))
                .doesNotContain(hash).doesNotContain("raw-token");
    }

    @Test
    @DisplayName("Reset: falha inesperada loga ERROR com userId e tipos, sem stacktrace/token/senha, e propaga")
    void shouldLogAndRethrowUnexpectedFailure(CapturedOutput output) {
        var tokenHash = TokenHasher.sha256Hex("raw-token");
        when(tokenRepository.consume(anyString(), any())).thenReturn(1);
        when(tokenRepository.findByTokenHash(tokenHash))
                .thenReturn(Optional.of(PasswordResetToken.builder().user(user).tokenHash(tokenHash)
                .expiresAt(OffsetDateTime.ofInstant(NOW.plusSeconds(3600), ZoneOffset.UTC)).used(true).build()));
        when(passwordEncoder.encode(anyString())).thenReturn("hash-novo");
        when(refreshTokenRepository.revokeAllByUserId(user.getId())).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.resetPassword("raw-token", "NovaSenha1"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(output.getOut()).contains("ERROR").contains("Falha ao redefinir senha (userId=" + user.getId() + ")")
                .contains("IllegalStateException").contains("causa raiz: IllegalStateException")
                .doesNotContain("\tat ").doesNotContain("raw-token").doesNotContain("NovaSenha1");
    }
}
