package com.autofolio.shared.exception;

/**
 * Exceção lançada quando o refresh token não existe, está revogado ou expirou.
 * A mensagem é única de propósito, para não revelar o motivo.
 */
public class InvalidRefreshTokenException extends RuntimeException {
    /**
     * Cria a exceção com mensagem genérica para qualquer refresh token inválido.
     */
    public InvalidRefreshTokenException() {
        super("Refresh token inválido ou expirado.");
    }
}
