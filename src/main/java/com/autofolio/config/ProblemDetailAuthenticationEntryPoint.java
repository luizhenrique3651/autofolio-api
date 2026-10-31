package com.autofolio.config;

import com.autofolio.shared.exception.ProblemDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.BearerTokenError;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * Responde 401 (ou 400, quando o erro do Bearer é invalid_request) do resource server com
 * ProblemDetail (RFC 7807), igual ao GlobalExceptionHandler.
 * Mantém o header WWW-Authenticate definido pelo entry point padrão do Bearer.
 */
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final BearerTokenAuthenticationEntryPoint delegate = new BearerTokenAuthenticationEntryPoint();
    private final ObjectMapper objectMapper;

    public ProblemDetailAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        delegate.commence(request, response, authException);

        // Mesma regra do delegate: BearerTokenError define o status (400 em invalid_request), senão 401
        var status = authException instanceof OAuth2AuthenticationException oauth
                && oauth.getError() instanceof BearerTokenError bearerError
                ? bearerError.getHttpStatus()
                : HttpStatus.UNAUTHORIZED;
        var problemDetail = status == HttpStatus.BAD_REQUEST
                ? ProblemDetails.badRequest("Requisição de autenticação inválida.")
                : ProblemDetails.unauthorized("Autenticação necessária ou token inválido.");

        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), problemDetail);
    }
}
