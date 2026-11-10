package com.autofolio.auth.security;

import org.springframework.security.oauth2.jwt.BadJwtException;

/**
 * A consulta de conta ativa falhou por problema de infraestrutura (ex.: banco fora). Não é token
 * inválido, e sim 503.
 *
 * <p>Estende {@code BadJwtException} só pelo caminho no Spring Security: o
 * {@code JwtAuthenticationProvider} a converte em {@code InvalidBearerTokenException} (com esta
 * exceção como causa), que o {@code BearerTokenAuthenticationFilter} entrega ao entry point; o
 * entry point detecta a causa via {@code getCause()} e responde 503. Uma exceção de outro tipo
 * não chegaria ao entry point: o filtro a relançaria ao container (página de erro do Tomcat, fora
 * do ProblemDetail).
 */
public class AccountLookupUnavailableException extends BadJwtException {

    /**
     * Cria a exceção preservando a causa para o log.
     */
    public AccountLookupUnavailableException(Throwable cause) {
        super("Falha ao consultar a conta do usuário autenticado.", cause);
    }
}
