package com.autofolio.auth.repository;

import com.autofolio.auth.domain.UserProfile;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositório dos perfis de usuário.
 */
@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
}
