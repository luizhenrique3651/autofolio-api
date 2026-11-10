package com.autofolio.auth.repository;

import com.autofolio.auth.domain.User;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repositório dos usuários.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    /**
     * Indica se já existe usuário com o e-mail informado.
     */
    boolean existsByEmail(String email);

    /**
     * Indica se já existe usuário com o slug informado.
     */
    boolean existsBySlug(String slug);

    /**
     * Indica se outro usuário (de id diferente) já usa o slug informado.
     */
    boolean existsBySlugAndIdNot(String slug, UUID id);

    /**
     * Busca o usuário pelo e-mail.
     */
    Optional<User> findByEmail(String email);

    /**
     * Indica se existe conta ativa com o id. Consulta leve por chave primária, usada pelo
     * validador do JWT a cada requisição autenticada.
     */
    boolean existsByIdAndActiveTrue(UUID id);

    /**
     * Atualiza o hash da senha de uma conta ativa sem sobrescrever os demais campos (a entidade
     * lida fora de transação fica desatualizada). Devolve 0 se a conta foi desativada no meio.
     */
    @Modifying
    @Query("update User u set u.password = :hash, u.updatedAt = :now "
            + "where u.id = :id and u.active = true")
    int updatePassword(@Param("id") UUID id, @Param("hash") String hash,
            @Param("now") OffsetDateTime now);

    /**
     * Troca o slug de uma conta ativa sem sobrescrever os demais campos. Limpa o contexto de
     * persistência (a entidade carregada fica desatualizada). Devolve 0 se a conta foi desativada
     * no meio; viola a unique se outro usuário tomou o slug.
     */
    @Modifying(clearAutomatically = true)
    @Query("update User u set u.slug = :slug, u.updatedAt = :now "
            + "where u.id = :id and u.active = true")
    int updateSlug(@Param("id") UUID id, @Param("slug") String slug,
            @Param("now") OffsetDateTime now);

    /**
     * Desativa a conta (soft delete). Devolve 0 se já estava inativa.
     */
    @Modifying
    @Query("update User u set u.active = false, u.updatedAt = :now "
            + "where u.id = :id and u.active = true")
    int deactivate(@Param("id") UUID id, @Param("now") OffsetDateTime now);
}
