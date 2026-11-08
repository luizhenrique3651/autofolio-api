package com.autofolio.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO de confirmação de senha para desativar a conta.
 */
public record DeleteAccountRequest(
        @NotBlank(message = "A senha é obrigatória.")
        @Size(max = 255, message = "A senha deve ter no máximo 255 caracteres.")
        String password
) {
}
