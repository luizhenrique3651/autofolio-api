package com.autofolio.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.autofolio.auth.domain.User;
import com.autofolio.auth.domain.UserProfile;
import com.autofolio.auth.repository.UserProfileRepository;
import com.autofolio.shared.exception.InvalidImageException;
import com.autofolio.shared.exception.InvalidSessionException;
import com.autofolio.shared.exception.MediaStorageException;
import com.autofolio.shared.validation.TestImages;
import com.autofolio.user.dto.UpdateProfileRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
@DisplayName("Testes Unitários - ProfileService")
class ProfileServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private ProfilePhotoStorage photoStorage;

    private ProfileService service;

    private static final byte[] PNG = TestImages.png();
    private static final byte[] JPEG = TestImages.jpeg();
    private static final String URL = "https://res.cloudinary.com/x/image/upload/profiles/a.png";

    private final User user = User.builder().id(UUID.randomUUID())
            .name("Luiz").email("luiz@teste.com").password("hash").slug("luiz").build();

    @BeforeEach
    void setUp() {
        service = new ProfileService(userService, userProfileRepository, photoStorage,
                new TransactionTemplate(mock(PlatformTransactionManager.class)));
    }

    private static MockMultipartFile file(byte[] content) {
        return new MockMultipartFile("file", "foto.png", "image/png", content);
    }

    @Test
    @DisplayName("Atualiza os campos editáveis, preserva a foto e devolve o perfil")
    void shouldUpdateProfile(CapturedOutput output) {
        var profile = UserProfile.builder().user(user).photoUrl("http://foto").build();
        when(userService.loadUser(user.getId())).thenReturn(user);
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
        when(userService.loadUser(user.getId())).thenReturn(user);
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
        when(userService.loadUser(user.getId())).thenReturn(user);
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
        when(userService.loadUser(user.getId())).thenThrow(new InvalidSessionException());

        assertThatThrownBy(() -> service.update(user.getId(),
                new UpdateProfileRequest("Bio", null, null, null, 1)))
                .isInstanceOf(InvalidSessionException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Foto PNG ou JPEG: sobe, salva a URL no perfil existente e não vaza a URL no log")
    void shouldUploadPhoto(boolean png, CapturedOutput output) {
        var content = png ? PNG : JPEG;
        var profile = UserProfile.builder().user(user).bio("Bio").build();
        when(userService.loadUser(user.getId())).thenReturn(user);
        when(photoStorage.upload(user.getId(), content)).thenReturn(URL);
        when(userProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(profile));
        when(userProfileRepository.save(profile)).thenReturn(profile);

        var response = service.updatePhoto(user.getId(), file(content));

        assertThat(response.photoUrl()).isEqualTo(URL);
        assertThat(response.bio()).isEqualTo("Bio");
        assertThat(profile.getPhotoUrl()).isEqualTo(URL);
        assertThat(output.getOut()).contains("Foto de perfil atualizada (userId=" + user.getId());
    }

    @Test
    @DisplayName("Foto: cria o perfil quando o usuário ainda não tem registro")
    void shouldCreateProfileOnPhotoUpload() {
        when(userService.loadUser(user.getId())).thenReturn(user);
        when(photoStorage.upload(user.getId(), PNG)).thenReturn(URL);
        when(userProfileRepository.findByUserId(user.getId())).thenReturn(Optional.empty());
        when(userProfileRepository.save(any(UserProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.updatePhoto(user.getId(), file(PNG));

        var captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(user);
        assertThat(captor.getValue().getPhotoUrl()).isEqualTo(URL);
    }

    @Test
    @DisplayName("Foto: corrida na criação do perfil (user_id único) repete a gravação uma vez")
    void shouldRetrySaveOnConcurrentProfileCreation() {
        var existing = UserProfile.builder().user(user).build();
        when(userService.loadUser(user.getId())).thenReturn(user);
        when(photoStorage.upload(user.getId(), PNG)).thenReturn(URL);
        when(userProfileRepository.findByUserId(user.getId()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(userProfileRepository.save(any(UserProfile.class)))
                .thenThrow(new DataIntegrityViolationException("uk_user"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updatePhoto(user.getId(), file(PNG));

        assertThat(response.photoUrl()).isEqualTo(URL);
    }

    @Test
    @DisplayName("Foto: GIF, vazio e PNG/JPEG corrompido são recusados com WARN antes do upload")
    void shouldRejectInvalidImages(CapturedOutput output) {
        when(userService.loadUser(user.getId())).thenReturn(user);

        for (var content : new byte[][] {"GIF89a....".getBytes(StandardCharsets.US_ASCII),
                new byte[0], TestImages.corruptedPng(), TestImages.corruptedJpeg()}) {
            assertThatThrownBy(() -> service.updatePhoto(user.getId(), file(content)))
                    .isInstanceOf(InvalidImageException.class);
        }
        verifyNoInteractions(photoStorage, userProfileRepository);
        assertThat(output.getOut()).contains("WARN")
                .contains("Upload de foto recusado")
                .contains("userId=" + user.getId());
    }

    @Test
    @DisplayName("Foto: falha do Cloudinary propaga e nada é gravado")
    void shouldNotSaveWhenUploadFails() {
        when(userService.loadUser(user.getId())).thenReturn(user);
        when(photoStorage.upload(user.getId(), PNG))
                .thenThrow(new MediaStorageException("falhou", null));

        assertThatThrownBy(() -> service.updatePhoto(user.getId(), file(PNG)))
                .isInstanceOf(MediaStorageException.class);
        verifyNoInteractions(userProfileRepository);
    }

    @Test
    @DisplayName("Foto: conta desativada lança InvalidSessionException sem tocar no arquivo")
    void shouldRejectInactiveUserOnPhoto() {
        when(userService.loadUser(user.getId())).thenThrow(new InvalidSessionException());

        assertThatThrownBy(() -> service.updatePhoto(user.getId(), file(PNG)))
                .isInstanceOf(InvalidSessionException.class);
        verifyNoInteractions(photoStorage, userProfileRepository);
    }
}
