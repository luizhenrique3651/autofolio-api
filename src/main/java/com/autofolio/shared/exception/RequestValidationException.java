package com.autofolio.shared.exception;

import java.util.List;

/**
 * Regra de validação que depende de contexto (ex.: o ano atual) e por isso não cabe numa
 * anotação do Bean Validation. Vira 400 com a lista {@code errors} de {@code {field, message}},
 * no mesmo formato das violações de corpo.
 */
public class RequestValidationException extends RuntimeException {

    private final transient List<FieldValidationError> errors;

    /**
     * Cria a exceção com os erros por campo devolvidos ao cliente.
     */
    public RequestValidationException(List<FieldValidationError> errors) {
        super("Falha na validação dos campos.");
        this.errors = List.copyOf(errors);
    }

    public List<FieldValidationError> getErrors() {
        return errors;
    }
}
