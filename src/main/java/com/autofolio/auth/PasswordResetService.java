package com.autofolio.auth;

import com.autofolio.auth.domain.PasswordResetToken;
import com.autofolio.auth.repository.PasswordResetTokenRepository;
import com.autofolio.auth.repository.RefreshTokenRepository;
import com.autofolio.auth.repository.UserRepository;
import com.autofolio.shared.exception.InvalidResetTokenException;
import com.autofolio.shared.security.TokenHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.mail.MailException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Fluxo de recuperação de senha. O pedido ("esqueci minha senha") roda inteiro em segundo plano para que a
 * resposta HTTP não dependa de o e-mail existir (tempo constante) nem do SMTP; por isso fica sem
 * {@code @Transactional}: cada acesso ao banco é curto e o envio SMTP acontece sem transação nem conexão JDBC aberta.
 * A redefinição, ao contrário, é uma única transação.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    static final Duration TOKEN_TTL = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetMailer mailer;
    private final Clock clock;

    /**
     * Gera o token e envia o e-mail se o usuário existir. Nunca propaga exceção.
     */
    @Async
    public void requestReset(String email) {
        UUID userId = null;
        try {
            var user = userRepository.findByEmail(email).orElse(null);
            if (user == null) {
                return;
            }
            userId = user.getId();
            var now = OffsetDateTime.now(clock);
            // Manutenção oportunista do usuário; falha aqui não impede gerar o token novo
            try {
                tokenRepository.deleteExpiredOrUsedByUserId(userId, now);
                tokenRepository.invalidateUnusedByUserId(userId);
            } catch (RuntimeException e) {
                log.warn("Falha na limpeza de tokens de redefinição (userId={}): {}", userId, describe(e));
            }

            // O banco guarda só o hash; o token cru vai apenas no e-mail
            var rawToken = UUID.randomUUID().toString();
            tokenRepository.save(PasswordResetToken.builder()
                    .user(user)
                    .tokenHash(TokenHasher.sha256Hex(rawToken))
                    .expiresAt(now.plus(TOKEN_TTL))
                    .used(false)
                    .build());
            mailer.send(user.getEmail(), user.getName(), rawToken, TOKEN_TTL);
        } catch (MailException e) {
            // A mensagem de exceção de e-mail traz o endereço (PII): ERROR só com os tipos (exceção e causa raiz).
            // A stacktrace completa vai para DEBUG, que só aparece em dev (em prod o nível é INFO).
            log.error("Falha ao enviar e-mail de redefinição de senha (userId={}): {}", userId, describe(e));
            log.debug("Stacktrace da falha de envio (userId={})", userId, e);
        } catch (Exception e) {
            log.error("Falha ao processar redefinição de senha (userId={})", userId, e);
        }
    }

    /**
     * Redefine a senha com o token de recuperação. Tudo numa transação: se qualquer passo falhar, o token
     * não é consumido. O consumo é um UPDATE condicional, então dois pedidos simultâneos com o mesmo token
     * resultam em um sucesso e um InvalidResetTokenException.
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        var tokenHash = TokenHasher.sha256Hex(rawToken);
        if (tokenRepository.consume(tokenHash, OffsetDateTime.now(clock)) == 0) {
            log.warn("Redefinição de senha recusada: token inválido, expirado ou já utilizado (tokenHashPrefix={})",
                    tokenHash.substring(0, 8));
            throw new InvalidResetTokenException();
        }

        UUID userId = null;
        try {
            var user = tokenRepository.findByTokenHash(tokenHash)
                    .orElseThrow(() -> new IllegalStateException("Token consumido não encontrado"))
                    .getUser();
            userId = user.getId();
            user.setPassword(passwordEncoder.encode(newPassword));
            tokenRepository.invalidateUnusedByUserId(userId);
            int revoked = refreshTokenRepository.revokeAllByUserId(userId);
            log.info("Senha redefinida (userId={}, refreshTokensRevogados={})", userId, revoked);
        } catch (RuntimeException e) {
            // Sem stacktrace: o catch-all do GlobalExceptionHandler já a registra
            log.error("Falha ao redefinir senha (userId={}): {}", userId, describe(e));
            throw e;
        }
    }

    // Só os tipos (exceção e causa raiz): a mensagem pode trazer PII e a stacktrace fica a cargo de quem trata a exceção
    private static String describe(Throwable e) {
        return e.getClass().getSimpleName()
                + " (causa raiz: " + NestedExceptionUtils.getMostSpecificCause(e).getClass().getSimpleName() + ")";
    }
}
