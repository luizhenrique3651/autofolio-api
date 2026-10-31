package com.autofolio.auth;

import com.autofolio.auth.domain.RefreshToken;
import com.autofolio.auth.domain.User;
import com.autofolio.auth.repository.RefreshTokenRepository;
import com.autofolio.shared.exception.InvalidRefreshTokenException;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Testes Unitários - TokenService")
class TokenServiceTest {

    private final SecretKeySpec key = new SecretKeySpec(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
    private final TokenService tokenService =
            new TokenService(new NimbusJwtEncoder(new ImmutableSecret<>(key)), repository, clock);

    @Test
    @DisplayName("Deve emitir JWT HS256 com claims corretas e persistir refresh token de 30 dias")
    void shouldIssueTokens() {
        var user = User.builder().id(UUID.randomUUID()).slug("luiz-dev").build();

        var tokens = tokenService.issueTokens(user);

        var jwt = decoderWithoutExpValidation().decode(tokens.accessToken());
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("slug")).isEqualTo("luiz-dev");
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(1));
        assertThat(tokens.expiresIn()).isEqualTo(3600);

        var captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getToken()).isEqualTo(tokens.refreshToken());
        assertThat(UUID.fromString(saved.getToken())).isNotNull();
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.isRevoked()).isFalse();
        assertThat(saved.getExpiresAt().toInstant()).isEqualTo(clock.instant().plus(Duration.ofDays(30)));
        assertThat(jwt.getIssuedAt()).isEqualTo(clock.instant());
    }

    @Test
    @DisplayName("Refresh: token válido gera novo access token e não rotaciona nem altera o refresh")
    void shouldRefresh() {
        var user = User.builder().id(UUID.randomUUID()).slug("luiz-dev").build();
        storedToken(user, clock.instant().plusSeconds(60), false);

        var response = tokenService.refresh("rt");

        var jwt = decoderWithoutExpValidation().decode(response.accessToken());
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("slug")).isEqualTo("luiz-dev");
        assertThat(response.expiresIn()).isEqualTo(3600);
        verify(repository, never()).save(any());
        verify(repository, never()).deleteTokenById(any());
    }

    @Test
    @DisplayName("Refresh: token inexistente lança InvalidRefreshTokenException")
    void shouldRejectUnknown() {
        when(repository.findByToken("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tokenService.refresh("x")).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    @DisplayName("Refresh: token revogado lança exceção e não é removido")
    void shouldRejectRevoked() {
        storedToken(User.builder().id(UUID.randomUUID()).build(), clock.instant().plusSeconds(60), true);

        assertThatThrownBy(() -> tokenService.refresh("rt")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(repository, never()).deleteTokenById(any());
    }

    @Test
    @DisplayName("Refresh: token expirado lança exceção e é removido")
    void shouldRejectAndDeleteExpired() {
        var rt = storedToken(User.builder().id(UUID.randomUUID()).build(), clock.instant().minusSeconds(1), false);

        assertThatThrownBy(() -> tokenService.refresh("rt")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(repository).deleteTokenById(rt.getId());
    }

    @Test
    @DisplayName("Refresh: token que expira exatamente agora é considerado expirado")
    void shouldTreatBoundaryAsExpired() {
        var rt = storedToken(User.builder().id(UUID.randomUUID()).build(), clock.instant(), false);

        assertThatThrownBy(() -> tokenService.refresh("rt")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(repository).deleteTokenById(rt.getId());
    }

    private RefreshToken storedToken(User user, Instant expiresAt, boolean revoked) {
        var rt = RefreshToken.builder().id(UUID.randomUUID()).user(user).token("rt").revoked(revoked)
                .expiresAt(OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC)).build();
        when(repository.findByToken("rt")).thenReturn(Optional.of(rt));
        return rt;
    }

    // Clock fixo no passado: desliga a validação de exp para checar só as claims
    private NimbusJwtDecoder decoderWithoutExpValidation() {
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(token -> OAuth2TokenValidatorResult.success());
        return decoder;
    }
}
