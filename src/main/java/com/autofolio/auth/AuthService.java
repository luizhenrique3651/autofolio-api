package com.autofolio.auth;

import com.autofolio.auth.domain.User;
import com.autofolio.auth.domain.UserProfile;
import com.autofolio.auth.dto.AuthResponse;
import com.autofolio.auth.dto.RegisterRequest;
import com.autofolio.auth.repository.UserProfileRepository;
import com.autofolio.auth.repository.UserRepository;
import com.autofolio.shared.exception.DataConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serviço responsável por operações de autenticação e gerenciamento de usuários.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Registra um novo vendedor no sistema.
     * Realiza a persistência atômica do Usuário e seu Perfil.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Validação de duplicidade
        if (userRepository.existsByEmail(request.email())) {
            throw new DataConflictException("E-mail já cadastrado no sistema.");
        }
        if (userRepository.existsBySlug(request.slug())) {
            throw new DataConflictException("Slug já está em uso por outro usuário.");
        }

        // Criação e persistência do Usuário
        var user = User.builder()
                .name(request.name())
                .email(request.email())
                .slug(request.slug())
                .password(passwordEncoder.encode(request.password()))
                .build();

        var savedUser = userRepository.save(user);

        // Criação e persistência do Perfil associado (1:1)
        var profile = UserProfile.builder()
                .user(savedUser)
                .build();

        userProfileRepository.save(profile);

        // Mock de geração de tokens (Substituir por JWTService real futuramente)
        String mockAccessToken = "mock-access-token-for-" + savedUser.getEmail();
        String mockRefreshToken = "mock-refresh-token-for-" + savedUser.getEmail();

        var userSummary = new AuthResponse.UserSummary(
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getSlug()
        );

        return new AuthResponse(mockAccessToken, mockRefreshToken, userSummary);
    }
}
