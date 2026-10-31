package com.autofolio.auth;

import com.autofolio.auth.domain.RefreshToken;
import com.autofolio.auth.domain.User;
import com.autofolio.auth.repository.RefreshTokenRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("Testes Unitários - TokenService")
class TokenServiceTest {

    private final SecretKeySpec key = new SecretKeySpec(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
    private final TokenService tokenService =
            new TokenService(new NimbusJwtEncoder(new ImmutableSecret<>(key)), repository);

    @Test
    @DisplayName("Deve emitir JWT HS256 com claims corretas e persistir refresh token de 30 dias")
    void shouldIssueTokens() {
        var user = User.builder().id(UUID.randomUUID()).slug("luiz-dev").build();

        var tokens = tokenService.issueTokens(user);

        var jwt = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build()
                .decode(tokens.accessToken());
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
        assertThat(saved.getExpiresAt()).isBetween(
                OffsetDateTime.now().plusDays(30).minusMinutes(1),
                OffsetDateTime.now().plusDays(30).plusMinutes(1));
    }
}
