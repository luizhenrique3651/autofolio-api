package com.autofolio.auth;

import com.autofolio.auth.domain.User;
import com.autofolio.auth.domain.UserProfile;
import com.autofolio.auth.dto.LoginRequest;
import com.autofolio.auth.dto.RefreshRequest;
import com.autofolio.auth.dto.AccessTokenGrant;
import com.autofolio.auth.dto.RegisterRequest;
import com.autofolio.auth.dto.TokenPair;
import com.autofolio.shared.exception.InvalidCredentialsException;
import com.autofolio.auth.repository.UserProfileRepository;
import com.autofolio.auth.repository.UserRepository;
import com.autofolio.shared.exception.DataConflictException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Testes Unitários - AuthService")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("Deve registrar um novo vendedor com sucesso")
    void shouldRegisterNewSellerSuccessfully() {
        var request = new RegisterRequest("Luiz", "luiz@example.com", "luiz-dev", "password123");
        var user = User.builder()
                .email(request.email())
                .name(request.name())
                .slug(request.slug())
                .build();

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsBySlug(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(tokenService.issueTokens(user)).thenReturn(new TokenPair("access", "refresh", 3600));

        var response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        assertThat(response.user().email()).isEqualTo(request.email());
        assertThat(response.user().slug()).isEqualTo(request.slug());

        verify(userRepository, times(1)).save(any(User.class));
        verify(userProfileRepository, times(1)).save(any(UserProfile.class));
        verify(passwordEncoder, times(1)).encode(request.password());
    }

    @Test
    @DisplayName("Deve lançar DataConflictException quando o e-mail já existe")
    void shouldThrowExceptionWhenEmailAlreadyExists() {
        // Arrange
        var request = new RegisterRequest("Luiz", "exists@example.com", "luiz-dev", "password123");
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DataConflictException.class)
                .hasMessageContaining("E-mail já cadastrado");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve lançar DataConflictException quando o slug já existe")
    void shouldThrowExceptionWhenSlugAlreadyExists() {
        var request = new RegisterRequest("Luiz", "luiz@example.com", "slug-exists", "password123");
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsBySlug(request.slug())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DataConflictException.class)
                .hasMessageContaining("Slug já está em uso");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve autenticar com credenciais válidas e retornar os tokens")
    void shouldLoginSuccessfully() {
        var user = User.builder().email("luiz@example.com").password("hash").build();
        var tokens = new TokenPair("access", "refresh", 3600);
        when(userRepository.findByEmail("luiz@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hash")).thenReturn(true);
        when(tokenService.issueTokens(user)).thenReturn(tokens);

        var response = authService.login(new LoginRequest("luiz@example.com", "password123"));

        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        assertThat(response.expiresIn()).isEqualTo(3600);
    }

    @Test
    @DisplayName("Deve lançar InvalidCredentialsException quando o e-mail não existe")
    void shouldFailLoginWhenUserNotFound() {
        when(userRepository.findByEmail("x@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(eq("password123"), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("x@example.com", "password123")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(passwordEncoder).matches(eq("password123"), anyString());
        verify(tokenService, never()).issueTokens(any());
    }

    @Test
    @DisplayName("Deve lançar InvalidCredentialsException quando a senha está errada")
    void shouldFailLoginWhenPasswordWrong() {
        var user = User.builder().email("luiz@example.com").password("hash").build();
        when(userRepository.findByEmail("luiz@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("luiz@example.com", "wrong")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Credenciais inválidas.");

        verify(tokenService, never()).issueTokens(any());
    }

    @Test
    @DisplayName("Deve delegar a renovação ao TokenService")
    void shouldRefresh() {
        when(tokenService.refresh("rt")).thenReturn(new AccessTokenGrant("novo", 3600));

        var response = authService.refresh(new RefreshRequest("rt"));

        assertThat(response.accessToken()).isEqualTo("novo");
        assertThat(response.expiresIn()).isEqualTo(3600);
    }
}
