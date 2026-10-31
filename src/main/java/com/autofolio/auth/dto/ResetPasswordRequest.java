package com.autofolio.auth.dto;

import com.autofolio.shared.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO para redefinição de senha com o token recebido por e-mail.
 */
public record ResetPasswordRequest(
    @NotBlank(message = "O token é obrigatório.")
    @Size(max = 255, message = "O token deve ter no máximo 255 caracteres.")
    String token,

    @NotBlank(message = "A nova senha é obrigatória.")
    @StrongPassword
    String newPassword
) {
}
