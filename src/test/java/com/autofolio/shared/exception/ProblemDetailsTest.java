package com.autofolio.shared.exception;

import com.autofolio.shared.web.RequestIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Testes Unitários - ProblemDetails (requestId)")
class ProblemDetailsTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("build e normalize incluem o requestId do MDC")
    void shouldIncludeRequestIdFromMdc() {
        MDC.put(RequestIdFilter.MDC_KEY, "rid-1");
        var built = ProblemDetails.badRequest("x");
        var normalized = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);

        ProblemDetails.normalize(normalized);

        assertThat(built.getProperties()).containsEntry("requestId", "rid-1");
        assertThat(normalized.getProperties()).containsEntry("requestId", "rid-1");
    }

    @Test
    @DisplayName("Sem MDC, não inclui a property")
    void shouldOmitWhenNoMdc() {
        assertThat(ProblemDetails.badRequest("x").getProperties()).doesNotContainKey("requestId");
    }
}
