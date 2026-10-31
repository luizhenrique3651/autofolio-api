package com.autofolio.auth.repository;

import com.autofolio.auth.domain.PasswordResetToken;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    @EntityGraph(attributePaths = "user")
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Consome o token de forma atômica: só marca como usado se ainda estiver válido. Sob concorrência,
     * apenas uma chamada recebe 1; as demais esperam o lock da linha, reavaliam o filtro e recebem 0.
     */
    @Modifying
    @Query("update PasswordResetToken t set t.used = true "
            + "where t.tokenHash = :tokenHash and t.used = false and t.expiresAt > :now")
    int consume(@Param("tokenHash") String tokenHash, @Param("now") OffsetDateTime now);

    /**
     * Invalida os tokens ainda não usados do usuário (um novo pedido substitui os anteriores).
     */
    @Transactional
    @Modifying
    @Query("update PasswordResetToken t set t.used = true where t.user.id = :userId and t.used = false")
    void invalidateUnusedByUserId(@Param("userId") UUID userId);

    /**
     * Remove os tokens expirados ou já usados do usuário.
     */
    @Transactional
    @Modifying
    @Query("delete from PasswordResetToken t "
            + "where t.user.id = :userId and (t.used = true or t.expiresAt <= :now)")
    void deleteExpiredOrUsedByUserId(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);
}
