package com.autofolio.shared.exception;

/**
 * Recurso inexistente ou que pertence a outro usuário (ex.: veículo alheio): ambos viram 404,
 * sem revelar a diferença.
 */
public class EntityNotFoundException extends RuntimeException {

    /**
     * Cria a exceção com a mensagem devolvida ao cliente.
     */
    public EntityNotFoundException(String message) {
        super(message);
    }
}
