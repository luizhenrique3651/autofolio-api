package com.autofolio.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

/**
 * Fábrica de ProblemDetail padronizados (RFC 7807).
 */
public final class ProblemDetails {

    private static final String TYPE_BASE = "https://autofolio.com/errors/";

    private record Label(String title, String slug) {
    }

    // Status com título em PT-BR; os demais usam o reasonPhrase do status
    private static final Map<HttpStatus, Label> LABELS = Map.of(
            HttpStatus.BAD_REQUEST, new Label("Requisição Inválida", "bad-request"),
            HttpStatus.UNAUTHORIZED, new Label("Não Autorizado", "unauthorized"),
            HttpStatus.NOT_FOUND, new Label("Recurso Não Encontrado", "not-found"),
            HttpStatus.METHOD_NOT_ALLOWED, new Label("Método Não Permitido", "method-not-allowed"),
            HttpStatus.NOT_ACCEPTABLE, new Label("Formato Não Aceito", "not-acceptable"),
            HttpStatus.CONFLICT, new Label("Conflito de Dados", "conflict"),
            HttpStatus.UNSUPPORTED_MEDIA_TYPE, new Label("Tipo de Mídia Não Suportado", "unsupported-media-type"),
            HttpStatus.INTERNAL_SERVER_ERROR, new Label("Erro Interno", "internal-error"));

    private ProblemDetails() {
    }

    public static ProblemDetail unauthorized(String detail) {
        return build(HttpStatus.UNAUTHORIZED, detail);
    }

    public static ProblemDetail badRequest(String detail) {
        return build(HttpStatus.BAD_REQUEST, detail);
    }

    public static ProblemDetail conflict(String detail) {
        return build(HttpStatus.CONFLICT, detail);
    }

    public static ProblemDetail internalError(String detail) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, detail);
    }

    /**
     * Padroniza ProblemDetail gerados pelo Spring MVC (404, 405, 415...): título em PT-BR,
     * type autofolio quando for about:blank e timestamp quando ausente.
     */
    public static void normalize(ProblemDetail problemDetail) {
        if (ProblemDetail.forStatus(problemDetail.getStatus()).getType().equals(problemDetail.getType())) {
            var label = labelFor(HttpStatusCode.valueOf(problemDetail.getStatus()));
            problemDetail.setTitle(label.title());
            problemDetail.setType(URI.create(TYPE_BASE + label.slug()));
        }
        if (problemDetail.getProperties() == null || !problemDetail.getProperties().containsKey("timestamp")) {
            problemDetail.setProperty("timestamp", Instant.now());
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
        return new Label(status.getReasonPhrase(), status.getReasonPhrase().toLowerCase(Locale.ROOT).replace(' ', '-'));
    }

    private static ProblemDetail build(HttpStatus status, String detail) {
        var label = labelFor(status);
        var problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(label.title());
        problemDetail.setType(URI.create(TYPE_BASE + label.slug()));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }
}
