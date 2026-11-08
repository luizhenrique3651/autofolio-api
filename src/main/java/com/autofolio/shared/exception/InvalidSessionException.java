package com.autofolio.shared.exception;

/**
 * Exceção lançada quando o JWT é válido, mas o usuário dono dele não existe mais ou foi
 * desativado. Responde 401 com a mesma mensagem do token inválido.
 */
public class InvalidSessionException extends RuntimeException {

    /** Mensagem genérica de 401, igual à do entry point do Spring Security. */
    public static final String MESSAGE = "Autenticação necessária ou token inválido.";

    /**
     * Cria a exceção com a mesma mensagem genérica do 401 de token inválido.
     */
    public InvalidSessionException() {
        super(MESSAGE);
    }
}
