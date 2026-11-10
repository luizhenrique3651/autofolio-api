package com.autofolio.shared.exception;

import java.util.Arrays;
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

    /**
     * Primeiro frame do código da AutoFolio na pilha (classe.método:linha), ignorando proxies
     * gerados ({@code $$SpringCGLIB$$}) e frames sem linha (-1).
     */
    public static String originFrame(Throwable e) {
        return Arrays.stream(e.getStackTrace())
                .filter(f -> f.getClassName().startsWith("com.autofolio.")
                        && !f.getClassName().contains("$$") && f.getLineNumber() >= 0)
                .findFirst()
                .map(f -> f.getClassName() + "." + f.getMethodName() + ":" + f.getLineNumber())
                .orElse("desconhecida");
    }

    /**
     * Nome da constraint violada (não é dado pessoal), quando a causa é do Hibernate; nunca vai
     * ao cliente.
     */
    public static String constraintName(Throwable e) {
        return e.getCause() instanceof org.hibernate.exception.ConstraintViolationException h
                ? h.getConstraintName() : null;
    }
}
