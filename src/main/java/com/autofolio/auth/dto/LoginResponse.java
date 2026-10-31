package com.autofolio.auth.dto;

/**
 * DTO de resposta do login. expiresIn em segundos.
 */
public record LoginResponse(
    String accessToken,
    String refreshToken,
    long expiresIn
) {
}
