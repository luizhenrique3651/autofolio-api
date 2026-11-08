package com.autofolio.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autofolio.auth.domain.User;
import com.autofolio.auth.domain.UserProfile;
import com.autofolio.auth.repository.UserProfileRepository;
import com.autofolio.shared.exception.InvalidSessionException;
import com.autofolio.user.dto.UpdateProfileRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
@DisplayName("Testes Unitários - ProfileService")
class ProfileServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private UserProfileRepository userProfileRepository;
    @InjectMocks
    private ProfileService service;

    private final User user = User.builder().id(UUID.randomUUID())
            .name("Luiz").email("luiz@teste.com").password("hash").slug("luiz").build();

    @Test
    @DisplayName("Atualiza os campos editáveis, preserva a foto e devolve o perfil")
    void shouldUpdateProfile(CapturedOutput output) {
        var profile = UserProfile.builder().user(user).photoUrl("http://foto").build();
        when(userService.requireActive(user.getId())).thenReturn(user);
        when(userProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(profile));
        when(userProfileRepository.save(profile)).thenReturn(profile);

        var response = service.update(user.getId(), new UpdateProfileRequest("Bio", "11999998888",
                "São Paulo", List.of("SUV", "Sedan"), 5));

        assertThat(response.bio()).isEqualTo("Bio");
        assertThat(response.whatsapp()).isEqualTo("11999998888");
        assertThat(response.city()).isEqualTo("São Paulo");
        assertThat(response.specialties()).containsExactly("SUV", "Sedan");
        assertThat(response.expYears()).isEqualTo(5);
        assertThat(response.photoUrl()).isEqualTo("http://foto");
        assertThat(output.getOut()).contains("Perfil atualizado (userId=" + user.getId())
                .doesNotContain("11999998888");
    }

    @Test
    @DisplayName("PUT completo: campos ausentes limpam o valor e anos nulo vira 0")
    void shouldClearMissingFields() {
        var profile = UserProfile.builder().user(user).bio("Antiga").whatsapp("11999998888")
                .city("RJ").specialties(List.of("SUV")).expYears(9).build();
        when(userService.requireActive(user.getId())).thenReturn(user);
        when(userProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(profile));
        when(userProfileRepository.save(profile)).thenReturn(profile);

        var response = service.update(user.getId(),
                new UpdateProfileRequest(null, null, null, null, null));

        assertThat(response.bio()).isNull();
        assertThat(response.whatsapp()).isNull();
        assertThat(response.city()).isNull();
        assertThat(response.specialties()).isEmpty();
        assertThat(response.expYears()).isZero();
    }

    @Test
    @DisplayName("Cria o perfil quando o usuário ainda não tem registro")
    void shouldCreateMissingProfile() {
        when(userService.requireActive(user.getId())).thenReturn(user);
        when(userProfileRepository.findByUserId(user.getId())).thenReturn(Optional.empty());
        when(userProfileRepository.save(any(UserProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.update(user.getId(), new UpdateProfileRequest("Bio", null, null, null, 1));

        var captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(user);
        assertThat(captor.getValue().getBio()).isEqualTo("Bio");
    }

    @Test
    @DisplayName("Conta inexistente ou desativada lança InvalidSessionException")
    void shouldRejectInactiveUser() {
        when(userService.requireActive(user.getId())).thenThrow(new InvalidSessionException());

        assertThatThrownBy(() -> service.update(user.getId(),
                new UpdateProfileRequest("Bio", null, null, null, 1)))
                .isInstanceOf(InvalidSessionException.class);
    }
}
