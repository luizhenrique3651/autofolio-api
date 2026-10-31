package com.autofolio.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO para renovação do access token.
 */
public record RefreshRequest(
    @NotBlank(message = "O refresh token é obrigatório.")
    @Size(max = 255, message = "O refresh token deve ter no máximo 255 caracteres.")
    String refreshToken
) {
}
