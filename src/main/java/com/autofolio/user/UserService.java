package com.autofolio.user;

import com.autofolio.auth.SessionRevoker;
import com.autofolio.auth.TokenService;
import com.autofolio.auth.domain.User;
import com.autofolio.auth.repository.UserProfileRepository;
import com.autofolio.auth.repository.UserRepository;
import com.autofolio.shared.exception.DataConflictException;
import com.autofolio.shared.exception.InvalidCredentialsException;
import com.autofolio.shared.exception.InvalidSessionException;
import com.autofolio.user.dto.ChangePasswordRequest;
import com.autofolio.user.dto.DeleteAccountRequest;
import com.autofolio.user.dto.ProfileResponse;
import com.autofolio.user.dto.SlugResponse;
import com.autofolio.user.dto.UpdateSlugRequest;
import com.autofolio.user.dto.UserMeResponse;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Operações do usuário autenticado sobre a própria conta: consulta, slug, senha e desativação.
 * Toda operação exige conta ativa; JWT de conta inexistente ou desativada gera 401. As que usam
 * BCrypt (troca de senha e desativação) não são {@code @Transactional}: o hash roda sem conexão
 * de banco aberta (ADR-001 §4.3) e só a gravação, junto da revogação dos tokens, usa transação
 * explícita.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final SessionRevoker sessionRevoker;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /**
     * Dados do usuário autenticado com o perfil.
     */
    @Transactional(readOnly = true)
    public UserMeResponse me(UUID userId) {
        var user = requireActive(userId);
        var profile = userProfileRepository.findByUserId(userId)
                .map(ProfileResponse::from)
                .orElseGet(ProfileResponse::empty);
        return UserMeResponse.of(user, profile);
    }

    /**
     * Troca o slug e devolve um access token novo com o claim atualizado. Repetir o slug atual é
     * idempotente.
     */
    @Transactional
    public SlugResponse updateSlug(UUID userId, UpdateSlugRequest request) {
        var user = requireActive(userId);
        var slug = request.slug();
        if (!slug.equals(user.getSlug())) {
            if (userRepository.existsBySlugAndIdNot(slug, userId)) {
                throw slugConflict(userId);
            }
            user.setSlug(slug);
            try {
                // Flush aqui para que a corrida entre duas trocas vire 409 e não 500
                userRepository.saveAndFlush(user);
            } catch (DataIntegrityViolationException e) {
                throw slugConflict(userId);
            }
            log.info("Slug alterado (userId={})", userId);
        }
        var grant = tokenService.issueAccessToken(user);
        return new SlugResponse(user.getSlug(), grant.accessToken(), grant.expiresIn());
    }

    /**
     * Troca a senha após conferir a atual e revoga todos os refresh tokens do usuário.
     */
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        var user = requireActive(userId);
        confirmPassword(userId, user.getPassword(), request.currentPassword(),
                "Troca de senha recusada: senha atual incorreta");
        var newHash = passwordEncoder.encode(request.newPassword());
        var revoked = transactionTemplate.execute(status -> {
            if (userRepository.updatePassword(userId, newHash, OffsetDateTime.now(clock)) == 0) {
                throw sessionLost(userId);
            }
            return sessionRevoker.revokeAll(userId);
        });
        log.info("Senha alterada (userId={}, refreshTokensRevogados={})", userId, revoked);
    }

    /**
     * Desativa a conta (soft delete) após conferir a senha, revogando refresh tokens e tokens de
     * redefinição pendentes. E-mail e slug continuam reservados.
     */
    public void deactivate(UUID userId, DeleteAccountRequest request) {
        var user = requireActive(userId);
        confirmPassword(userId, user.getPassword(), request.password(),
                "Desativação de conta recusada: senha incorreta");
        var revoked = transactionTemplate.execute(status -> {
            if (userRepository.deactivate(userId, OffsetDateTime.now(clock)) == 0) {
                throw sessionLost(userId);
            }
            return sessionRevoker.revokeAll(userId);
        });
        log.info("Conta desativada (userId={}, refreshTokensRevogados={})", userId, revoked);
    }

    User requireActive(UUID userId) {
        return userRepository.findByIdAndActiveTrue(userId).orElseThrow(() -> {
            log.warn("Acesso recusado: conta inexistente ou desativada (userId={})", userId);
            return new InvalidSessionException();
        });
    }

    private void confirmPassword(UUID userId, String hash, String rawPassword, String failure) {
        if (!passwordEncoder.matches(rawPassword, hash)) {
            log.warn("{} (userId={})", failure, userId);
            throw new InvalidCredentialsException();
        }
    }

    // Conta desativada entre a leitura e a gravação
    private InvalidSessionException sessionLost(UUID userId) {
        log.warn("Operação recusada: conta desativada durante a requisição (userId={})", userId);
        return new InvalidSessionException();
    }

    private DataConflictException slugConflict(UUID userId) {
        log.warn("Troca de slug recusada: slug em uso (userId={})", userId);
        return new DataConflictException("Slug já está em uso por outro usuário.");
    }
}
