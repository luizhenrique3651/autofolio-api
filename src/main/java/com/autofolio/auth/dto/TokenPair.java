package com.autofolio.auth.dto;

/**
 * Par de tokens emitido pelo TokenService. expiresIn (segundos) refere-se ao access token.
 */
public record TokenPair(
    String accessToken,
    String refreshToken,
    long expiresIn
) {
}
