package com.autofolio.auth;

import com.autofolio.auth.repository.PasswordResetTokenRepository;
import com.autofolio.auth.repository.RefreshTokenRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Encerra as sessões do usuário: revoga todos os refresh tokens e invalida os tokens de
 * redefinição de senha pendentes. Participa da transação de quem chama.
 */
@Component
@RequiredArgsConstructor
public class SessionRevoker {

    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    /**
     * Revoga as sessões e devolve quantos refresh tokens foram revogados.
     */
    public int revokeAll(UUID userId) {
        passwordResetTokenRepository.invalidateUnusedByUserId(userId);
        return refreshTokenRepository.revokeAllByUserId(userId);
    }
}
