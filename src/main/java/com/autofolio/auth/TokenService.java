package com.autofolio.auth;

import com.autofolio.auth.domain.RefreshToken;
import com.autofolio.auth.domain.User;
import com.autofolio.auth.dto.AccessTokenGrant;
import com.autofolio.auth.dto.TokenPair;
import com.autofolio.auth.repository.RefreshTokenRepository;
import com.autofolio.shared.exception.InvalidRefreshTokenException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emite o par de tokens (JWT de acesso + refresh token opaco persistido) no login e no cadastro,
 * e renova o access token a partir de um refresh token válido.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    static final Duration ACCESS_TOKEN_TTL = Duration.ofHours(1);
    static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);

    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    /**
     * Emite o access token e o refresh token do usuário e persiste o refresh token.
     */
    @Transactional
    public TokenPair issueTokens(User user) {
        Instant now = clock.instant();
        String accessToken = encodeAccessToken(user, now);

        var refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiresAt(OffsetDateTime.ofInstant(now.plus(REFRESH_TOKEN_TTL), ZoneOffset.UTC))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        return new TokenPair(accessToken, refreshToken.getToken(), ACCESS_TOKEN_TTL.toSeconds());
    }

    /**
     * Gera novo access token a partir de um refresh token válido, sem rotacioná-lo.
     * Token expirado é removido; a exceção não faz rollback para que a remoção seja efetivada.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AccessTokenGrant refresh(String token) {
        var refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(InvalidRefreshTokenException::new);
        Instant now = clock.instant();

        if (!refreshToken.getExpiresAt().toInstant().isAfter(now)) {
            refreshTokenRepository.deleteTokenById(refreshToken.getId());
            throw new InvalidRefreshTokenException();
        }
        // Conta desativada recebe o mesmo 401 de refresh token revogado
        if (!refreshToken.getUser().isActive()) {
            log.warn("Refresh recusado: conta desativada (userId={})",
                    refreshToken.getUser().getId());
        }
        if (refreshToken.isRevoked() || !refreshToken.getUser().isActive()) {
            throw new InvalidRefreshTokenException();
        }

        return new AccessTokenGrant(encodeAccessToken(refreshToken.getUser(), now),
                ACCESS_TOKEN_TTL.toSeconds());
    }

    /**
     * Emite apenas um novo access token (sem criar refresh token), por exemplo após a troca de
     * slug, que é um claim do JWT.
     */
    public AccessTokenGrant issueAccessToken(User user) {
        return new AccessTokenGrant(encodeAccessToken(user, clock.instant()),
                ACCESS_TOKEN_TTL.toSeconds());
    }

    private String encodeAccessToken(User user, Instant now) {
        var claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .claim("slug", user.getSlug())
                .issuedAt(now)
                .expiresAt(now.plus(ACCESS_TOKEN_TTL))
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
