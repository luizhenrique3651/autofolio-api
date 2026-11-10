package com.autofolio.shared.exception;

import jakarta.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
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
import org.springframework.validation.Errors;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
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
     * Converte recurso inexistente (ou de outro usuário) em 404.
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleEntityNotFoundException(EntityNotFoundException ex) {
        return ProblemDetails.notFound(ex.getMessage());
    }

    /**
     * Converte violação de integridade (ex.: unique em corrida) em 409 genérico, sem vazar o
     * nome da constraint nem SQL. A causa raiz fica no log.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        // A mensagem traz SQL com valores (dado pessoal): WARN só com tipos, constraint e o
        // ponto do código; a stacktrace completa vai para DEBUG (ADR-001 §1.6/§1.7)
        log.warn("Violação de integridade de dados: {} (constraint={}, origem={})",
                ExceptionSummary.describe(ex), ExceptionSummary.constraintName(ex),
                ExceptionSummary.originFrame(ex));
        log.debug("Stacktrace da violação de integridade", ex);
        return ProblemDetails.conflict("Conflito com o estado atual dos dados. "
                + "Atualize e tente novamente.");
    }

    /**
     * Converte violação de Bean Validation fora do corpo (ex.: parâmetros e services) em 400.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        var errors = ex.getConstraintViolations().stream()
                .map(v -> new FieldValidationError(lastNode(v.getPropertyPath().toString()),
                        v.getMessage()))
                .toList();
        return validationProblem(errors);
    }

    // Erros de campo com o field real; erros globais usam o nome do objeto (ou o do parâmetro)
    private static List<FieldValidationError> errorsOf(Errors errors, String globalName) {
        var result = new ArrayList<FieldValidationError>();
        errors.getFieldErrors().forEach(e -> result.add(
                new FieldValidationError(e.getField(), e.getDefaultMessage())));
        errors.getGlobalErrors().forEach(e -> result.add(new FieldValidationError(
                globalName == null ? e.getObjectName() : globalName, e.getDefaultMessage())));
        return result;
    }

    private static String lastNode(String path) {
        return path.substring(path.lastIndexOf('.') + 1);
    }

    private static ProblemDetail validationProblem(List<FieldValidationError> errors) {
        var problemDetail = ProblemDetails.badRequest("Falha na validação dos campos.");
        problemDetail.setProperty("errors", errors);
        return problemDetail;
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
        var errors = errorsOf(ex.getBindingResult(), null);
        return handleExceptionInternal(ex, validationProblem(errors), headers,
                HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            @NonNull HandlerMethodValidationException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        var errors = new ArrayList<FieldValidationError>();
        ex.getAllValidationResults().forEach(result -> {
            var name = result.getMethodParameter().getParameterName();
            var parameter = name == null ? "parâmetro" : name;
            if (result instanceof ParameterErrors parameterErrors) {
                // @Valid em objeto: mantém o campo real, não o nome do parâmetro
                errors.addAll(errorsOf(parameterErrors, parameter));
            } else {
                result.getResolvableErrors().forEach(e -> errors.add(
                        new FieldValidationError(parameter, e.getDefaultMessage())));
            }
        });
        return handleExceptionInternal(ex, validationProblem(errors), headers,
                HttpStatus.BAD_REQUEST, request);
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
        // 5xx do Spring MVC não passa pelo catch-all: loga aqui, uma vez (ADR-001 §1.8)
        if (statusCode.is5xxServerError()) {
            log.error("Erro do servidor ({})", statusCode.value(), ex);
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
        return ProblemDetails.internalError("Erro interno — tente novamente.");
    }
}
