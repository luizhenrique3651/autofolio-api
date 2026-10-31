package com.autofolio.auth.repository;

import com.autofolio.auth.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
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
     * Busca o usuário pelo e-mail.
     */
    Optional<User> findByEmail(String email);
}
