package com.autofolio.shared.security;

import com.autofolio.shared.exception.InvalidSessionException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Extrai o id do usuário autenticado (claim {@code sub}) do JWT de acesso.
 */
@Slf4j
public final class CurrentUser {

    private CurrentUser() {
    }

    /**
     * Devolve o id do usuário dono do token.
     */
    public static UUID id(Jwt jwt) {
        var subject = jwt.getSubject();
        if (subject == null) {
            throw rejected();
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            throw rejected();
        }
    }

    // Sem o conteúdo do token nem do sub (ADR-001 §1.6)
    private static InvalidSessionException rejected() {
        log.warn("JWT recusado: claim sub ausente ou não é um UUID");
        return new InvalidSessionException();
    }
}
