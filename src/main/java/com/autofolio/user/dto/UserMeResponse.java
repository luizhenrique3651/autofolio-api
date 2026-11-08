package com.autofolio.user.dto;

import com.autofolio.auth.domain.User;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Dados do usuário autenticado com o perfil (nunca inclui a senha).
 */
public record UserMeResponse(
        UUID id,
        String name,
        String email,
        String slug,
        OffsetDateTime createdAt,
        ProfileResponse profile
) {

    /**
     * Monta a resposta a partir do usuário e do perfil.
     */
    public static UserMeResponse of(User user, ProfileResponse profile) {
        return new UserMeResponse(user.getId(), user.getName(), user.getEmail(), user.getSlug(),
                user.getCreatedAt(), profile);
    }
}
