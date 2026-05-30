package com.autofolio.auth;

import com.autofolio.auth.domain.User;
import com.autofolio.auth.domain.UserProfile;
import com.autofolio.auth.dto.RegisterRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

        var response = authService.register(request);

        assertThat(response).isNotNull();
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
}
