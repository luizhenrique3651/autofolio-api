package com.autofolio.shared.exception;

/**
 * Exceção lançada quando o arquivo enviado não é uma imagem PNG ou JPEG válida.
 */
public class InvalidImageException extends RuntimeException {

    /**
     * Cria a exceção com a mensagem devolvida ao cliente.
     */
    public InvalidImageException(String message) {
        super(message);
    }
}
