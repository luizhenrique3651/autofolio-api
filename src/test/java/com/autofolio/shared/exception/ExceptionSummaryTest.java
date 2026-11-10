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
}
