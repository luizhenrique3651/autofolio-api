package com.autofolio.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Testes Unitários - ExceptionSummary")
class ExceptionSummaryTest {

    @Test
    @DisplayName("Resume só os tipos (exceção e causa raiz), nunca a mensagem")
    void shouldDescribeTypesOnly() {
        var e = new IOException("segredo@x.com", new SocketTimeoutException("lento"));

        assertThat(ExceptionSummary.describe(e))
                .isEqualTo("IOException (causa raiz: SocketTimeoutException)");
    }

    @Test
    @DisplayName("Sem causa, a raiz é a própria exceção")
    void shouldUseSelfAsRootCause() {
        assertThat(ExceptionSummary.describe(new IllegalStateException("x")))
                .isEqualTo("IllegalStateException (causa raiz: IllegalStateException)");
    }

    @Test
    @DisplayName("originFrame ignora proxies ($$) e frames sem linha")
    void shouldSkipProxyAndNoLineFrames() {
        var e = new IllegalStateException("x");
        e.setStackTrace(new StackTraceElement[] {
            new StackTraceElement("com.autofolio.auth.AuthService$$SpringCGLIB$$0", "register",
                    "<generated>", -1),
            new StackTraceElement("com.autofolio.auth.AuthService", "register",
                    "AuthService.java", 42)});

        assertThat(ExceptionSummary.originFrame(e))
                .isEqualTo("com.autofolio.auth.AuthService.register:42");
        e.setStackTrace(new StackTraceElement[0]);
        assertThat(ExceptionSummary.originFrame(e)).isEqualTo("desconhecida");
    }

    @Test
    @DisplayName("constraintName lê a constraint da causa do Hibernate; sem ela, nulo")
    void shouldReadConstraintName() {
        var cause = new org.hibernate.exception.ConstraintViolationException("x",
                new java.sql.SQLException("y"), "users_email_key");

        assertThat(ExceptionSummary.constraintName(
                new IllegalStateException("z", cause))).isEqualTo("users_email_key");
        assertThat(ExceptionSummary.constraintName(new IllegalStateException("z"))).isNull();
    }
}
