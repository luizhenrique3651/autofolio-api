package com.autofolio.auth.dto;

import java.util.UUID;

/**
 * DTO de resposta para operações de autenticação e cadastro.
 */
public record AuthResponse(
    String accessToken,
    String refreshToken,
    UserSummary user
) {
    public record UserSummary(
        UUID id,
        String name,
        String email,
        String slug
    ) {}
}
