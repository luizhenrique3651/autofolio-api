package com.autofolio.shared.exception;

/**
 * Exceção lançada quando o serviço externo de mídia (Cloudinary) falha ou não responde a tempo.
 */
public class MediaStorageException extends RuntimeException {

    /**
     * Cria a exceção com a mensagem devolvida ao cliente e a causa original (não exposta).
     */
    public MediaStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
