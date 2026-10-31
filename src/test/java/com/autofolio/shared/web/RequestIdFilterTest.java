package com.autofolio.shared.web;

import com.autofolio.config.AsyncConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Testes Unitários - RequestIdFilter e propagação do MDC")
class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    private String run(String header, AtomicReference<String> mdcInside, MockHttpServletResponse response) throws Exception {
        var request = new MockHttpServletRequest();
        if (header != null) {
            request.addHeader(RequestIdFilter.HEADER, header);
        }
        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(@NonNull ServletRequest req, @NonNull ServletResponse res) {
                mdcInside.set(MDC.get(RequestIdFilter.MDC_KEY));
            }
        });
        return response.getHeader(RequestIdFilter.HEADER);
    }

    @Test
    @DisplayName("Aceita X-Request-Id válido, coloca no MDC, devolve no header e limpa o MDC")
    void shouldKeepValidRequestId() throws Exception {
        var inside = new AtomicReference<String>();

        var header = run("abc-123_X.1", inside, new MockHttpServletResponse());

        assertThat(header).isEqualTo("abc-123_X.1");
        assertThat(inside.get()).isEqualTo("abc-123_X.1");
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("Gera UUID quando o header está ausente")
    void shouldGenerateWhenMissing() throws Exception {
        var inside = new AtomicReference<String>();

        var header = run(null, inside, new MockHttpServletResponse());

        assertThat(header).matches("[0-9a-f-]{36}");
        assertThat(inside.get()).isEqualTo(header);
    }

    @ParameterizedTest
    @ValueSource(strings = {"com espaço", "a\nb", "<script>", "", "x"})
    @DisplayName("Descarta valores com formato inválido")
    void shouldReplaceInvalid(String received) throws Exception {
        var inside = new AtomicReference<String>();

        var header = run(received, inside, new MockHttpServletResponse());

        if (received.equals("x")) {
            assertThat(header).isEqualTo("x");
        } else {
            assertThat(header).matches("[0-9a-f-]{36}");
        }
    }

    @Test
    @DisplayName("Descarta valor com mais de 64 caracteres")
    void shouldReplaceTooLong() throws Exception {
        var header = run("a".repeat(65), new AtomicReference<>(), new MockHttpServletResponse());

        assertThat(header).matches("[0-9a-f-]{36}");
    }

    @Test
    @DisplayName("TaskDecorator propaga o MDC para a tarefa e restaura o da thread executora")
    void taskDecoratorPropagatesMdc() throws Exception {
        var decorator = new AsyncConfig().mdcTaskDecorator();
        var seen = new AtomicReference<String>();
        MDC.put(RequestIdFilter.MDC_KEY, "req-1");
        var decorated = decorator.decorate(() -> seen.set(MDC.get(RequestIdFilter.MDC_KEY)));
        MDC.clear();

        var worker = new Thread(decorated);
        worker.start();
        worker.join();

        assertThat(seen.get()).isEqualTo("req-1");
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }
}
