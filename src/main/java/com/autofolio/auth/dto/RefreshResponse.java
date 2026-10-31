package com.autofolio.auth.dto;

/**
 * DTO de resposta da renovação. expiresIn em segundos.
 */
public record RefreshResponse(
    String accessToken,
    long expiresIn
) {
}
