package com.autofolio.shared.exception;

/**
 * Exceção lançada em credenciais inválidas. A mensagem é genérica de propósito,
 * para não revelar qual campo está incorreto.
 */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Credenciais inválidas.");
    }
}
