package com.autofolio.auth.dto;

/**
 * Access token emitido pelo TokenService na renovação. expiresIn em segundos.
 */
public record AccessTokenGrant(
    String accessToken,
    long expiresIn
) {
}
