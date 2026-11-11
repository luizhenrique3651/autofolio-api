package com.autofolio.auth.security;

import com.autofolio.auth.repository.UserRepository;
import com.autofolio.shared.exception.ExceptionSummary;
import com.autofolio.shared.exception.InvalidSessionException;
import com.autofolio.shared.security.CurrentUser;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Ponto único da regra "só conta ativa autentica" (AF-016; docs/backlog.md, histórico H5). Roda no
 * {@code JwtDecoder}, depois da assinatura e da expiração, só quando há Bearer (rotas públicas
 * ignoram o header).
 * Conta inexistente ou desativada, ou {@code sub} inválido, vira 401 pelo entry point do
 * resource server, com o mesmo ProblemDetail do token inválido. Falha de banco vira 503.
 *
 * <p>Custo: uma consulta {@code exists} por chave primária a cada requisição autenticada. Não há
 * cache: um TTL deixaria a conta desativada autenticar até expirar, e o cache em memória exigiria
 * dependência nova (ADR-001 §5.5).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActiveAccountValidator implements OAuth2TokenValidator<Jwt> {

    // Genérico de propósito: o header WWW-Authenticate não revela se a conta existe
    private static final OAuth2Error INVALID_TOKEN =
            new OAuth2Error("invalid_token", "Token inválido.", null);

    private final UserRepository userRepository;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        UUID userId;
        try {
            userId = CurrentUser.id(jwt);
        } catch (InvalidSessionException e) {
            // CurrentUser já logou o WARN
            return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
        }
        boolean active;
        try {
            active = userRepository.existsByIdAndActiveTrue(userId);
        } catch (DataAccessException e) {
            // Falha esperada de dependência externa (ADR-001 §1.7): 1 linha ERROR com userId e os
            // tipos da exceção e da causa raiz, sem mensagem nem stacktrace (a mensagem do driver
            // já sai no SqlExceptionHelper); a stacktrace vai para DEBUG
            log.error("Falha ao consultar a conta do usuário autenticado (userId={}, erro={})",
                    userId, ExceptionSummary.describe(e));
            log.debug("Stacktrace da falha ao consultar a conta (userId={})", userId, e);
            throw new AccountLookupUnavailableException(e);
        }
        if (active) {
            return OAuth2TokenValidatorResult.success();
        }
        log.warn("Acesso recusado: conta inexistente ou desativada (userId={})", userId);
        return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
    }
}
