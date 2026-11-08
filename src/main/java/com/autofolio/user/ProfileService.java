package com.autofolio.user;

import com.autofolio.auth.domain.UserProfile;
import com.autofolio.auth.repository.UserProfileRepository;
import com.autofolio.user.dto.ProfileResponse;
import com.autofolio.user.dto.UpdateProfileRequest;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atualização do perfil público do usuário autenticado.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserService userService;
    private final UserProfileRepository userProfileRepository;

    /**
     * Substitui os dados editáveis do perfil (a foto não é alterada). Cria o perfil se o usuário
     * ainda não tiver um.
     */
    @Transactional
    public ProfileResponse update(UUID userId, UpdateProfileRequest request) {
        var user = userService.requireActive(userId);
        var profile = userProfileRepository.findByUserId(userId)
                .orElseGet(() -> UserProfile.builder().user(user).build());

        profile.setBio(request.bio());
        profile.setWhatsapp(request.whatsapp());
        profile.setCity(request.city());
        profile.setSpecialties(request.specialties() == null
                ? new ArrayList<>() : new ArrayList<>(request.specialties()));
        profile.setExpYears(Objects.requireNonNullElse(request.expYears(), 0));

        var saved = userProfileRepository.save(profile);
        log.info("Perfil atualizado (userId={})", userId);
        return ProfileResponse.from(saved);
    }
}
