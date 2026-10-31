package com.autofolio.auth;

import com.autofolio.auth.domain.PasswordResetToken;
import com.autofolio.auth.repository.PasswordResetTokenRepository;
import com.autofolio.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.mail.MailException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Fluxo de "esqueci minha senha". Roda inteiro em segundo plano para que a resposta HTTP
 * não dependa de o e-mail existir (tempo constante) nem do SMTP.
 * Propositalmente sem @Transactional: cada acesso ao banco é curto e o envio SMTP
 * acontece sem transação nem conexão JDBC aberta.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    static final Duration TOKEN_TTL = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
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
            var resetToken = PasswordResetToken.builder()
                    .user(user)
                    .token(UUID.randomUUID().toString())
                    .expiresAt(OffsetDateTime.now(clock).plus(TOKEN_TTL))
                    .used(false)
                    .build();
            tokenRepository.save(resetToken);
            mailer.send(user.getEmail(), user.getName(), resetToken.getToken(), TOKEN_TTL);
        } catch (MailException e) {
            // A mensagem de exceção de e-mail traz o endereço (PII): ERROR só com os tipos (exceção e causa raiz).
            // A stacktrace completa vai para DEBUG, que só aparece em dev (em prod o nível é INFO).
            log.error("Falha ao enviar e-mail de redefinição de senha (userId={}): {} (causa raiz: {})", userId,
                    e.getClass().getSimpleName(), NestedExceptionUtils.getMostSpecificCause(e).getClass().getSimpleName());
            log.debug("Stacktrace da falha de envio (userId={})", userId, e);
        } catch (Exception e) {
            log.error("Falha ao processar redefinição de senha (userId={})", userId, e);
        }
    }
}
