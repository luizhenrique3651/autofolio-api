package com.autofolio.shared.exception;

/**
 * Exceção lançada quando o token de redefinição de senha é inexistente, expirado ou já utilizado.
 * A mensagem é única de propósito, para não revelar o motivo.
 */
public class InvalidResetTokenException extends RuntimeException {
    public InvalidResetTokenException() {
        super("Token de redefinição inválido, expirado ou já utilizado.");
    }
}
