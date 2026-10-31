package com.autofolio.shared.exception;

/**
 * Exceção lançada em credenciais inválidas. A mensagem é genérica de propósito,
 * para não revelar qual campo está incorreto.
 */
public class InvalidCredentialsException extends RuntimeException {
    /**
     * Cria a exceção com mensagem genérica, que não revela qual credencial falhou.
     */
    public InvalidCredentialsException() {
        super("Credenciais inválidas.");
    }
}
