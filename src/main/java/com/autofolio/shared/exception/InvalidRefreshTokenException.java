package com.autofolio.shared.exception;

/**
 * Exceção lançada quando o refresh token não existe, está revogado ou expirou.
 * A mensagem é única de propósito, para não revelar o motivo.
 */
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() {
        super("Refresh token inválido ou expirado.");
    }
}
