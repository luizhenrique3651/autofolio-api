package com.autofolio.user.dto;

import com.autofolio.shared.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO para troca de senha do usuário autenticado.
 */
public record ChangePasswordRequest(
        @NotBlank(message = "A senha atual é obrigatória.")
        @Size(max = 255, message = "A senha atual deve ter no máximo 255 caracteres.")
        String currentPassword,

        @NotBlank(message = "A nova senha é obrigatória.")
        @StrongPassword
        String newPassword
) {
}
