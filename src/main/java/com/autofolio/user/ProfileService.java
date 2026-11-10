package com.autofolio.user;

import com.autofolio.auth.domain.User;
import com.autofolio.auth.domain.UserProfile;
import com.autofolio.auth.repository.UserProfileRepository;
import com.autofolio.shared.exception.InvalidImageException;
import com.autofolio.shared.validation.ImageValidator;
import com.autofolio.user.dto.ProfileResponse;
import com.autofolio.user.dto.UpdateProfileRequest;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Atualização do perfil público do usuário autenticado: dados editáveis e foto.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserService userService;
    private final UserProfileRepository userProfileRepository;
    private final ProfilePhotoStorage photoStorage;
    private final TransactionTemplate transactionTemplate;

    /**
     * Substitui os dados editáveis do perfil (a foto não é alterada). Cria o perfil se o usuário
     * ainda não tiver um.
     */
    @Transactional
    public ProfileResponse update(UUID userId, UpdateProfileRequest request) {
        var user = userService.loadUser(userId);
        var profile = findOrNew(user);

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

    /**
     * Valida a imagem, envia ao Cloudinary e grava a URL em {@code photo_url}, criando o perfil
     * se não existir. O upload (I/O lento) roda sem transação nem conexão aberta (ADR-001
     * §4.1); só a gravação da URL usa transação. A foto anterior não é apagada do Cloudinary.
     */
    public ProfileResponse updatePhoto(UUID userId, MultipartFile file) {
        var user = userService.loadUser(userId);
        var content = readAndValidate(userId, file);
        var url = photoStorage.upload(userId, content);
        UserProfile saved;
        try {
            saved = savePhotoUrl(user, url);
        } catch (DataIntegrityViolationException e) {
            // Dois primeiros uploads simultâneos: o outro criou o perfil (user_id é único)
            saved = savePhotoUrl(user, url);
        }
        log.info("Foto de perfil atualizada (userId={})", userId);
        return ProfileResponse.from(saved);
    }

    private UserProfile savePhotoUrl(User user, String url) {
        return transactionTemplate.execute(status -> {
            var profile = findOrNew(user);
            profile.setPhotoUrl(url);
            return userProfileRepository.save(profile);
        });
    }

    // Valida o conteúdo real (ver ImageValidator); recusa do cliente é WARN, não ERROR
    private byte[] readAndValidate(UUID userId, MultipartFile file) {
        try {
            return ImageValidator.readAndValidate(file);
        } catch (InvalidImageException e) {
            log.warn("Upload de foto recusado: {} (userId={})", e.getMessage(), userId);
            throw e;
        }
    }

    private UserProfile findOrNew(User user) {
        return userProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> UserProfile.builder().user(user).build());
    }
}
