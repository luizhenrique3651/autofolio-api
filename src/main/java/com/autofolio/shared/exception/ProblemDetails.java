package com.autofolio.shared.exception;

import com.autofolio.shared.web.RequestIdFilter;
import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Fábrica de ProblemDetail padronizados (RFC 7807).
 */
public final class ProblemDetails {

    private static final String TYPE_BASE = "https://autofolio.com/errors/";

    private record Label(String title, String slug) {
    }

    // Status com título em PT-BR; os demais usam o reasonPhrase do status
    private static final Map<HttpStatus, Label> LABELS = Map.ofEntries(
            Map.entry(HttpStatus.BAD_REQUEST, new Label("Requisição Inválida", "bad-request")),
            Map.entry(HttpStatus.UNAUTHORIZED, new Label("Não Autorizado", "unauthorized")),
            Map.entry(HttpStatus.NOT_FOUND, new Label("Recurso Não Encontrado", "not-found")),
            Map.entry(HttpStatus.METHOD_NOT_ALLOWED,
                    new Label("Método Não Permitido", "method-not-allowed")),
            Map.entry(HttpStatus.NOT_ACCEPTABLE, new Label("Formato Não Aceito", "not-acceptable")),
            Map.entry(HttpStatus.CONFLICT, new Label("Conflito de Dados", "conflict")),
            Map.entry(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    new Label("Tipo de Mídia Não Suportado", "unsupported-media-type")),
            Map.entry(HttpStatus.PAYLOAD_TOO_LARGE,
                    new Label("Arquivo Muito Grande", "payload-too-large")),
            Map.entry(HttpStatus.INTERNAL_SERVER_ERROR,
                    new Label("Erro Interno", "internal-error")),
            Map.entry(HttpStatus.BAD_GATEWAY, new Label("Falha em Serviço Externo", "bad-gateway")),
            Map.entry(HttpStatus.SERVICE_UNAVAILABLE,
                    new Label("Serviço Indisponível", "service-unavailable")));

    private ProblemDetails() {
    }

    /**
     * Cria o ProblemDetail de 401.
     */
    public static ProblemDetail unauthorized(String detail) {
        return build(HttpStatus.UNAUTHORIZED, detail);
    }

    /**
     * Cria o ProblemDetail de 400.
     */
    public static ProblemDetail badRequest(String detail) {
        return build(HttpStatus.BAD_REQUEST, detail);
    }

    /**
     * Cria o ProblemDetail de 404.
     */
    public static ProblemDetail notFound(String detail) {
        return build(HttpStatus.NOT_FOUND, detail);
    }

    /**
     * Cria o ProblemDetail de 409.
     */
    public static ProblemDetail conflict(String detail) {
        return build(HttpStatus.CONFLICT, detail);
    }

    /**
     * Cria o ProblemDetail de 413.
     */
    public static ProblemDetail payloadTooLarge(String detail) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, detail);
    }

    /**
     * Cria o ProblemDetail de 502.
     */
    public static ProblemDetail badGateway(String detail) {
        return build(HttpStatus.BAD_GATEWAY, detail);
    }

    /**
     * Cria o ProblemDetail de 503.
     */
    public static ProblemDetail serviceUnavailable(String detail) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, detail);
    }

    /**
     * Cria o ProblemDetail de 500.
     */
    public static ProblemDetail internalError(String detail) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, detail);
    }

    /**
     * Padroniza ProblemDetail gerados pelo Spring MVC (404, 405, 415...): título em PT-BR,
     * type autofolio quando for about:blank e timestamp quando ausente.
     */
    public static void normalize(ProblemDetail problemDetail) {
        if (ProblemDetail.forStatus(problemDetail.getStatus()).getType()
                .equals(problemDetail.getType())) {
            var label = labelFor(HttpStatusCode.valueOf(problemDetail.getStatus()));
            problemDetail.setTitle(label.title());
            problemDetail.setType(URI.create(TYPE_BASE + label.slug()));
        }
        var properties = problemDetail.getProperties();
        if (properties == null || !properties.containsKey("timestamp")) {
            problemDetail.setProperty("timestamp", Instant.now());
        }
        addCommonProperties(problemDetail);
    }

    /**
     * Define {@code path} e {@code instance} (URI da requisição) quando o chamador já tem a
     * requisição em mãos, como o entry point do resource server. Se a URI não for válida, omite
     * o {@code instance}.
     */
    public static void setPath(ProblemDetail problemDetail, String requestUri) {
        problemDetail.setProperty("path", requestUri);
        try {
            problemDetail.setInstance(URI.create(requestUri));
        } catch (IllegalArgumentException e) {
            problemDetail.setInstance(null);
        }
    }

    private static Label labelFor(HttpStatusCode code) {
        var status = HttpStatus.resolve(code.value());
        if (status == null) {
            return new Label("Erro", code.is5xxServerError() ? "internal-error" : "error");
        }
        var label = LABELS.get(status);
        if (label != null) {
            return label;
        }
        return new Label(status.getReasonPhrase(), status.getReasonPhrase().toLowerCase(Locale.ROOT)
                .replace(' ', '-'));
    }

    private static ProblemDetail build(HttpStatus status, String detail) {
        var label = labelFor(status);
        var problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(label.title());
        problemDetail.setType(URI.create(TYPE_BASE + label.slug()));
        problemDetail.setProperty("timestamp", Instant.now());
        addCommonProperties(problemDetail);
        return problemDetail;
    }

    // error (reason phrase do status), path (URI da requisição) e requestId, em todo erro
    private static void addCommonProperties(ProblemDetail problemDetail) {
        var status = HttpStatus.resolve(problemDetail.getStatus());
        if (status != null) {
            problemDetail.setProperty("error", status.getReasonPhrase());
        }
        var requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes attributes) {
            problemDetail.setProperty("path", attributes.getRequest().getRequestURI());
        }
        addRequestId(problemDetail);
    }

    // Permite ao cliente citar o mesmo id que aparece nos logs e no header X-Request-Id
    private static void addRequestId(ProblemDetail problemDetail) {
        var requestId = MDC.get(RequestIdFilter.MDC_KEY);
        if (requestId != null) {
            problemDetail.setProperty("requestId", requestId);
        }
    }
}
