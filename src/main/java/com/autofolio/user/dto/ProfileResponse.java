package com.autofolio.user.dto;

import com.autofolio.auth.domain.UserProfile;
import java.util.List;

/**
 * Perfil público do usuário.
 */
public record ProfileResponse(
        String bio,
        String whatsapp,
        String city,
        String photoUrl,
        List<String> specialties,
        Integer expYears
) {

    /**
     * Perfil vazio, para usuário que ainda não tem registro de perfil.
     */
    public static ProfileResponse empty() {
        return new ProfileResponse(null, null, null, null, List.of(), 0);
    }

    /**
     * Converte a entidade no DTO de resposta.
     */
    public static ProfileResponse from(UserProfile profile) {
        return new ProfileResponse(profile.getBio(), profile.getWhatsapp(), profile.getCity(),
                profile.getPhotoUrl(), List.copyOf(profile.getSpecialties()),
                profile.getExpYears());
    }
}
