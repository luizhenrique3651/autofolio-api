package com.autofolio.shared.exception;

import org.springframework.core.NestedExceptionUtils;

/**
 * Resumo de exceção seguro para log (ADR-001 §1.7).
 */
public final class ExceptionSummary {

    private ExceptionSummary() {
    }

    /**
     * Só os tipos (exceção e causa raiz): a mensagem pode trazer dado pessoal ou credencial, e a
     * stacktrace fica a cargo de quem trata a exceção.
     */
    public static String describe(Throwable e) {
        var rootCause = NestedExceptionUtils.getMostSpecificCause(e);
        return e.getClass().getSimpleName()
                + " (causa raiz: " + rootCause.getClass().getSimpleName() + ")";
    }
}
