package com.autofolio.auth;

import com.autofolio.auth.domain.User;
import com.autofolio.auth.domain.UserProfile;
import com.autofolio.auth.dto.AuthResponse;
import com.autofolio.auth.dto.LoginRequest;
import com.autofolio.auth.dto.LoginResponse;
import com.autofolio.auth.dto.RegisterRequest;
import com.autofolio.auth.repository.UserProfileRepository;
import com.autofolio.auth.repository.UserRepository;
import com.autofolio.shared.exception.DataConflictException;
import com.autofolio.shared.exception.InvalidCredentialsException;
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

    // Hash BCrypt (força 12) fixo, usado só para equalizar o tempo quando o e-mail não existe
    private static final String DUMMY_HASH = "$2y$12$5QvHSTxjhCEuWSZ8YX6A5etZA05yBFZSr4xjhsJ6oezMP4mx.Bb/S";

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

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

        var tokens = tokenService.issueTokens(savedUser);

        var userSummary = new AuthResponse.UserSummary(
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getSlug()
        );

        return new AuthResponse(tokens.accessToken(), tokens.refreshToken(), userSummary);
    }

    /**
     * Autentica por e-mail e senha. Qualquer falha gera a mesma exceção genérica.
     */
    public LoginResponse login(LoginRequest request) {
        var user = userRepository.findByEmail(request.email()).orElse(null);
        if (user == null) {
            // Gasta o mesmo tempo de BCrypt para não revelar a existência da conta pelo tempo de resposta
            passwordEncoder.matches(request.password(), DUMMY_HASH);
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        var tokens = tokenService.issueTokens(user);
        return new LoginResponse(tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn());
    }
}
