package com.autofolio.shared.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

/**
 * Manipulador global de exceções para garantir respostas padronizadas (RFC 7807).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(DataConflictException.class)
    public ProblemDetail handleDataConflictException(DataConflictException ex) {
        return ProblemDetails.conflict(ex.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentialsException(InvalidCredentialsException ex) {
        return ProblemDetails.unauthorized(ex.getMessage());
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ProblemDetail handleInvalidRefreshTokenException(InvalidRefreshTokenException ex) {
        return ProblemDetails.unauthorized(ex.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(@NonNull MethodArgumentNotValidException ex,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatusCode status,
                                                                  @NonNull WebRequest request) {
        var details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        var problemDetail = ProblemDetails.badRequest("Falha na validação dos campos.");
        problemDetail.setProperty("errors", details);
        return handleExceptionInternal(ex, problemDetail, headers, HttpStatus.BAD_REQUEST, request);
    }

    // Mensagem genérica, sem expor detalhes do parser
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(@NonNull HttpMessageNotReadableException ex,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatusCode status,
                                                                  @NonNull WebRequest request) {
        var problemDetail = ProblemDetails.badRequest("Corpo da requisição ausente ou malformado.");
        return handleExceptionInternal(ex, problemDetail, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    @Nullable
    protected ResponseEntity<Object> handleExceptionInternal(@NonNull Exception ex, @Nullable Object body,
                                                             @NonNull HttpHeaders headers,
                                                             @NonNull HttpStatusCode statusCode,
                                                             @NonNull WebRequest request) {
        // Para exceções padrão do Spring o body chega nulo; resolve-o aqui para poder padronizá-lo
        Object resolvedBody = body;
        if (resolvedBody == null && ex instanceof ErrorResponse errorResponse) {
            resolvedBody = errorResponse.updateAndGetBody(getMessageSource(), LocaleContextHolder.getLocale());
        }
        if (resolvedBody instanceof ProblemDetail problemDetail) {
            ProblemDetails.normalize(problemDetail);
        }
        return super.handleExceptionInternal(ex, resolvedBody, headers, statusCode, request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneralException(Exception ex) {
        // Erros de segurança são tratados pelo Spring Security (401/403), não são 500
        if (ex instanceof AccessDeniedException accessDenied) {
            throw accessDenied;
        }
        if (ex instanceof AuthenticationException authentication) {
            throw authentication;
        }
        log.error("Erro inesperado", ex);
        return ProblemDetails.internalError("Ocorreu um erro inesperado no servidor.");
    }
}
