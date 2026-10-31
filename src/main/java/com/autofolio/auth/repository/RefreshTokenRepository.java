package com.autofolio.auth.repository;

import com.autofolio.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    @EntityGraph(attributePaths = "user")
    Optional<RefreshToken> findByToken(String token);

    /**
     * Remove por id sem carregar a entidade: idempotente sob concorrência (0 linhas não é erro).
     */
    @Modifying
    @Query("delete from RefreshToken r where r.id = :id")
    void deleteTokenById(@Param("id") UUID id);
}
