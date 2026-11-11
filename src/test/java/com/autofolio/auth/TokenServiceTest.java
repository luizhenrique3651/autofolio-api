package com.autofolio.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autofolio.auth.domain.RefreshToken;
import com.autofolio.auth.domain.User;
import com.autofolio.auth.repository.RefreshTokenRepository;
import com.autofolio.shared.exception.InvalidRefreshTokenException;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Testes Unitários - TokenService")
class TokenServiceTest {

    private static final String TOKEN = "tok-secreto-123";

    private final SecretKeySpec key = new SecretKeySpec(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
    private final TokenService tokenService =
            new TokenService(new NimbusJwtEncoder(new ImmutableSecret<>(key)), repository, clock);

    @Test
    @DisplayName("Deve emitir JWT HS256 com claims corretas e persistir refresh token de 30 dias")
    void shouldIssueTokens() {
        var user = user();

        var tokens = tokenService.issueTokens(user);

        var jwt = decoderWithoutExpValidation().decode(tokens.accessToken());
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("slug")).isEqualTo("luiz-dev");
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()))
                .isEqualTo(Duration.ofHours(1));
        assertThat(tokens.expiresIn()).isEqualTo(3600);

        var captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getToken()).isEqualTo(tokens.refreshToken());
        assertThat(UUID.fromString(saved.getToken())).isNotNull();
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.isRevoked()).isFalse();
        assertThat(saved.getExpiresAt().toInstant())
                .isEqualTo(clock.instant().plus(Duration.ofDays(30)));
        assertThat(jwt.getIssuedAt()).isEqualTo(clock.instant());
    }

    @Test
    @DisplayName("Refresh: token válido gera novo access token e não rotaciona nem altera o "
            + "refresh")
    void shouldRefresh() {
        var user = user();
        storedToken(user, clock.instant().plusSeconds(60), false);

        var response = tokenService.refresh(TOKEN);

        var jwt = decoderWithoutExpValidation().decode(response.accessToken());
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("slug")).isEqualTo("luiz-dev");
        assertThat(response.expiresIn()).isEqualTo(3600);
        verify(repository, never()).save(any());
        verify(repository, never()).deleteTokenById(any());
    }

    @Test
    @DisplayName("Refresh: token inexistente lança InvalidRefreshTokenException")
    void shouldRejectUnknown(CapturedOutput output) {
        when(repository.findByToken(TOKEN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tokenService.refresh(TOKEN))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThat(output.getOut()).contains("Refresh recusado: token inexistente")
                .doesNotContain(TOKEN).doesNotContain("userId");
    }

    @Test
    @DisplayName("Refresh: token revogado lança exceção e não é removido")
    void shouldRejectRevoked(CapturedOutput output) {
        var user = user();
        storedToken(user, clock.instant().plusSeconds(60), true);

        assertThatThrownBy(() -> tokenService.refresh(TOKEN))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(repository, never()).deleteTokenById(any());
        assertThat(output.getOut())
                .contains("Refresh recusado: token revogado (userId=" + user.getId() + ")")
                .doesNotContain("luiz@example.com").doesNotContain(TOKEN);
    }

    @Test
    @DisplayName("Refresh: conta desativada lança a mesma exceção do token revogado")
    void shouldRejectInactiveUser(CapturedOutput output) {
        var user = user();
        user.setActive(false);
        storedToken(user, clock.instant().plusSeconds(60), false);

        assertThatThrownBy(() -> tokenService.refresh(TOKEN))
                .isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(output.getOut()).contains("conta desativada (userId=" + user.getId() + ")")
                .doesNotContain("luiz@example.com").doesNotContain(TOKEN);
    }

    @Test
    @DisplayName("issueAccessToken: emite só o JWT, com o slug atual, sem criar refresh token")
    void shouldIssueAccessTokenOnly() {
        var user = user();
        user.setSlug("novo-slug");

        var grant = tokenService.issueAccessToken(user);

        var jwt = decoderWithoutExpValidation().decode(grant.accessToken());
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("slug")).isEqualTo("novo-slug");
        assertThat(grant.expiresIn()).isEqualTo(3600);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Refresh: token expirado lança exceção e é removido")
    void shouldRejectAndDeleteExpired(CapturedOutput output) {
        var user = user();
        var rt = storedToken(user, clock.instant().minusSeconds(1), false);

        assertThatThrownBy(() -> tokenService.refresh(TOKEN))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(repository).deleteTokenById(rt.getId());
        assertThat(output.getOut())
                .contains("Refresh recusado: token expirado (userId=" + user.getId() + ")")
                .doesNotContain("luiz@example.com").doesNotContain(TOKEN);
    }

    @Test
    @DisplayName("Refresh: token que expira exatamente agora é considerado expirado")
    void shouldTreatBoundaryAsExpired() {
        var rt = storedToken(user(), clock.instant(), false);

        assertThatThrownBy(() -> tokenService.refresh(TOKEN))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(repository).deleteTokenById(rt.getId());
    }

    private static User user() {
        return User.builder().id(UUID.randomUUID()).name("Luiz").email("luiz@example.com")
                .password("hash").slug("luiz-dev").build();
    }

    private RefreshToken storedToken(User user, Instant expiresAt, boolean revoked) {
        var rt = RefreshToken.builder().id(UUID.randomUUID()).user(user).token(TOKEN)
                .revoked(revoked)
                .expiresAt(OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC)).build();
        when(repository.findByToken(TOKEN)).thenReturn(Optional.of(rt));
        return rt;
    }

    // Clock fixo no passado: desliga a validação de exp para checar só as claims
    private NimbusJwtDecoder decoderWithoutExpValidation() {
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(token -> OAuth2TokenValidatorResult.success());
        return decoder;
    }
}
