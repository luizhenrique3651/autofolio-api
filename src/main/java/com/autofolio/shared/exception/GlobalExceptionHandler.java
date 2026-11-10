package com.autofolio.shared.exception;

import java.util.stream.Collectors;
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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Manipulador global de exceções para garantir respostas padronizadas (RFC 7807).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * Converte conflito de dados em 409.
     */
    @ExceptionHandler(DataConflictException.class)
    public ProblemDetail handleDataConflictException(DataConflictException ex) {
        return ProblemDetails.conflict(ex.getMessage());
    }

    /**
     * Converte credenciais inválidas em 401.
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentialsException(InvalidCredentialsException ex) {
        return ProblemDetails.unauthorized(ex.getMessage());
    }

    /**
     * Converte refresh token inválido em 401.
     */
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ProblemDetail handleInvalidRefreshTokenException(InvalidRefreshTokenException ex) {
        return ProblemDetails.unauthorized(ex.getMessage());
    }

    /**
     * Converte sessão de usuário inexistente ou desativado em 401.
     */
    @ExceptionHandler(InvalidSessionException.class)
    public ProblemDetail handleInvalidSessionException(InvalidSessionException ex) {
        return ProblemDetails.unauthorized(ex.getMessage());
    }

    /**
     * Converte token de redefinição inválido em 400.
     */
    @ExceptionHandler(InvalidResetTokenException.class)
    public ProblemDetail handleInvalidResetTokenException(InvalidResetTokenException ex) {
        return ProblemDetails.badRequest(ex.getMessage());
    }

    /**
     * Converte imagem inválida (formato ou conteúdo) em 400.
     */
    @ExceptionHandler(InvalidImageException.class)
    public ProblemDetail handleInvalidImageException(InvalidImageException ex) {
        return ProblemDetails.badRequest(ex.getMessage());
    }

    /**
     * Converte falha do serviço de mídia em 502. O log ERROR com a causa já saiu onde a falha
     * foi capturada (ADR-001 §1.8: não loga de novo aqui).
     */
    @ExceptionHandler(MediaStorageException.class)
    public ProblemDetail handleMediaStorageException(MediaStorageException ex) {
        return ProblemDetails.badGateway(ex.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            @NonNull MaxUploadSizeExceededException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        var problemDetail = ProblemDetails.payloadTooLarge(
                "O arquivo excede o tamanho máximo permitido.");
        return handleExceptionInternal(ex, problemDetail, headers, HttpStatus.PAYLOAD_TOO_LARGE,
                request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            @NonNull MethodArgumentNotValidException ex,
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
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            @NonNull HttpMessageNotReadableException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        var problemDetail = ProblemDetails.badRequest("Corpo da requisição ausente ou malformado.");
        return handleExceptionInternal(ex, problemDetail, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    @Nullable
    protected ResponseEntity<Object> handleExceptionInternal(@NonNull Exception ex,
            @Nullable Object body,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode statusCode,
            @NonNull WebRequest request) {
        // Para exceções padrão do Spring o body chega nulo; resolve-o aqui para poder padronizá-lo
        Object resolvedBody = body;
        if (resolvedBody == null && ex instanceof ErrorResponse errorResponse) {
            resolvedBody = errorResponse.updateAndGetBody(getMessageSource(),
                    LocaleContextHolder.getLocale());
        }
        if (resolvedBody instanceof ProblemDetail problemDetail) {
            ProblemDetails.normalize(problemDetail);
        }
        return super.handleExceptionInternal(ex, resolvedBody, headers, statusCode, request);
    }

    /**
     * Converte qualquer falha inesperada em 500, sem expor detalhes ao cliente.
     */
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
