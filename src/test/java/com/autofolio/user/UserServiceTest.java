package com.autofolio.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.autofolio.auth.SessionRevoker;
import com.autofolio.auth.TokenService;
import com.autofolio.auth.domain.User;
import com.autofolio.auth.domain.UserProfile;
import com.autofolio.auth.dto.AccessTokenGrant;
import com.autofolio.auth.repository.UserProfileRepository;
import com.autofolio.auth.repository.UserRepository;
import com.autofolio.shared.exception.DataConflictException;
import com.autofolio.shared.exception.InvalidCredentialsException;
import com.autofolio.shared.exception.InvalidSessionException;
import com.autofolio.user.dto.ChangePasswordRequest;
import com.autofolio.user.dto.DeleteAccountRequest;
import com.autofolio.user.dto.UpdateSlugRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
@DisplayName("Testes Unitários - UserService")
class UserServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private SessionRevoker sessionRevoker;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenService tokenService;

    private PlatformTransactionManager transactionManager;
    private UserService service;
    private User user;

    @BeforeEach
    void setUp() {
        transactionManager = mock(PlatformTransactionManager.class);
        service = new UserService(userRepository, userProfileRepository, sessionRevoker,
                passwordEncoder, tokenService,
                new TransactionTemplate(transactionManager),
                Clock.fixed(NOW, ZoneOffset.UTC));
        user = User.builder().id(UUID.randomUUID()).name("Luiz").email("luiz@teste.com")
                .password("hash").slug("luiz-dev").build();
    }

    private void activeUser() {
        when(userRepository.findByIdAndActiveTrue(user.getId())).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("me: devolve usuário e perfil, sem o hash da senha")
    void shouldReturnMe() {
        activeUser();
        var profile = UserProfile.builder().user(user).bio("Bio").city("SP")
                .specialties(List.of("SUV")).expYears(3).build();
        when(userProfileRepository.findByUserId(user.getId())).thenReturn(Optional.of(profile));

        var response = service.me(user.getId());

        assertThat(response.id()).isEqualTo(user.getId());
        assertThat(response.email()).isEqualTo("luiz@teste.com");
        assertThat(response.profile().bio()).isEqualTo("Bio");
        assertThat(response.profile().specialties()).containsExactly("SUV");
        assertThat(response.toString()).doesNotContain("hash");
    }

    @Test
    @DisplayName("me: sem registro de perfil devolve perfil vazio")
    void shouldReturnEmptyProfile() {
        activeUser();
        when(userProfileRepository.findByUserId(user.getId())).thenReturn(Optional.empty());

        var response = service.me(user.getId());

        assertThat(response.profile().specialties()).isEmpty();
        assertThat(response.profile().expYears()).isZero();
    }

    @Test
    @DisplayName("me: conta inexistente ou desativada lança InvalidSessionException e loga o "
            + "userId")
    void shouldRejectInactiveOnMe(CapturedOutput output) {
        when(userRepository.findByIdAndActiveTrue(user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.me(user.getId()))
                .isInstanceOf(InvalidSessionException.class);

        assertThat(output.getOut()).contains("userId=" + user.getId());
    }

    @Test
    @DisplayName("Slug: grava, faz flush e devolve token novo")
    void shouldUpdateSlug(CapturedOutput output) {
        activeUser();
        when(userRepository.existsBySlugAndIdNot("novo-slug", user.getId())).thenReturn(false);
        when(tokenService.issueAccessToken(user)).thenReturn(new AccessTokenGrant("jwt", 3600));

        var response = service.updateSlug(user.getId(), new UpdateSlugRequest("novo-slug"));

        assertThat(user.getSlug()).isEqualTo("novo-slug");
        assertThat(response.slug()).isEqualTo("novo-slug");
        assertThat(response.accessToken()).isEqualTo("jwt");
        verify(userRepository).saveAndFlush(user);
        assertThat(output.getOut()).contains("Slug alterado (userId=" + user.getId());
    }

    @Test
    @DisplayName("Slug: o mesmo slug atual é idempotente, sem consulta de unicidade nem gravação")
    void shouldAcceptSameSlug() {
        activeUser();
        when(tokenService.issueAccessToken(user)).thenReturn(new AccessTokenGrant("jwt", 3600));

        var response = service.updateSlug(user.getId(), new UpdateSlugRequest("luiz-dev"));

        assertThat(response.slug()).isEqualTo("luiz-dev");
        verify(userRepository, never()).existsBySlugAndIdNot(anyString(), any());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Slug: em uso por outro usuário lança DataConflictException")
    void shouldRejectSlugInUse(CapturedOutput output) {
        activeUser();
        when(userRepository.existsBySlugAndIdNot("ocupado", user.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.updateSlug(user.getId(), new UpdateSlugRequest("ocupado")))
                .isInstanceOf(DataConflictException.class);

        assertThat(user.getSlug()).isEqualTo("luiz-dev");
        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(tokenService);
        assertThat(output.getOut()).contains("userId=" + user.getId());
    }

    @Test
    @DisplayName("Slug: corrida detectada pela UNIQUE no flush vira DataConflictException")
    void shouldTurnUniqueViolationIntoConflict() {
        activeUser();
        when(userRepository.existsBySlugAndIdNot("corrida", user.getId())).thenReturn(false);
        when(userRepository.saveAndFlush(user))
                .thenThrow(new DataIntegrityViolationException("uk_slug"));

        assertThatThrownBy(() -> service.updateSlug(user.getId(), new UpdateSlugRequest("corrida")))
                .isInstanceOf(DataConflictException.class);
        verifyNoInteractions(tokenService);
    }

    @Test
    @DisplayName("Senha: confere a atual, grava o hash novo e revoga os refresh tokens")
    void shouldChangePassword(CapturedOutput output) {
        activeUser();
        when(passwordEncoder.matches("SenhaAtual1", "hash")).thenReturn(true);
        when(passwordEncoder.encode("SenhaNova1")).thenReturn("hash-novo");
        when(userRepository.updatePassword(Mockito.eq(user.getId()), Mockito.eq("hash-novo"),
                any())).thenReturn(1);
        when(sessionRevoker.revokeAll(user.getId())).thenReturn(2);

        service.changePassword(user.getId(),
                new ChangePasswordRequest("SenhaAtual1", "SenhaNova1"));

        InOrder order = Mockito.inOrder(userRepository, sessionRevoker);
        order.verify(userRepository).updatePassword(Mockito.eq(user.getId()),
                Mockito.eq("hash-novo"), any());
        order.verify(sessionRevoker).revokeAll(user.getId());
        assertThat(output.getOut()).contains("userId=" + user.getId())
                .doesNotContain("SenhaAtual1").doesNotContain("SenhaNova1")
                .doesNotContain("hash-novo");
    }

    @Test
    @DisplayName("Senha: atual incorreta lança InvalidCredentialsException e não grava nada")
    void shouldRejectWrongCurrentPassword(CapturedOutput output) {
        activeUser();
        when(passwordEncoder.matches("errada", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(user.getId(),
                new ChangePasswordRequest("errada", "SenhaNova1")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(passwordEncoder, never()).encode(anyString());
        verifyNoInteractions(transactionManager, sessionRevoker);
        assertThat(output.getOut()).contains("userId=" + user.getId())
                .doesNotContain("errada");
    }

    @Test
    @DisplayName("Senha: conta desativada entre leitura e gravação lança 401")
    void shouldRejectChangePasswordWhenDeactivatedMeanwhile(CapturedOutput output) {
        activeUser();
        when(passwordEncoder.matches("SenhaAtual1", "hash")).thenReturn(true);
        when(passwordEncoder.encode("SenhaNova1")).thenReturn("hash-novo");
        when(userRepository.updatePassword(Mockito.eq(user.getId()), anyString(), any()))
                .thenReturn(0);

        assertThatThrownBy(() -> service.changePassword(user.getId(),
                new ChangePasswordRequest("SenhaAtual1", "SenhaNova1")))
                .isInstanceOf(InvalidSessionException.class);
        verify(sessionRevoker, never()).revokeAll(any());
    }

    @Test
    @DisplayName("Senha: conta desativada lança InvalidSessionException antes de conferir a senha")
    void shouldRejectChangePasswordOfInactiveUser() {
        when(userRepository.findByIdAndActiveTrue(user.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changePassword(user.getId(),
                new ChangePasswordRequest("a", "SenhaNova1")))
                .isInstanceOf(InvalidSessionException.class);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("Desativar: confere a senha, marca inativa e revoga refresh e reset tokens")
    void shouldDeactivate(CapturedOutput output) {
        activeUser();
        when(passwordEncoder.matches("SenhaAtual1", "hash")).thenReturn(true);
        when(userRepository.deactivate(Mockito.eq(user.getId()), any())).thenReturn(1);
        when(sessionRevoker.revokeAll(user.getId())).thenReturn(1);

        service.deactivate(user.getId(), new DeleteAccountRequest("SenhaAtual1"));

        verify(userRepository).deactivate(Mockito.eq(user.getId()), any());
        verify(sessionRevoker).revokeAll(user.getId());
        assertThat(output.getOut()).contains("Conta desativada (userId=" + user.getId())
                .doesNotContain("SenhaAtual1");
    }

    @Test
    @DisplayName("Desativar: senha incorreta lança InvalidCredentialsException e não desativa")
    void shouldRejectDeactivateWithWrongPassword() {
        activeUser();
        when(passwordEncoder.matches("errada", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.deactivate(user.getId(),
                new DeleteAccountRequest("errada")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(userRepository, never()).deactivate(any(), any());
        verifyNoInteractions(transactionManager, sessionRevoker);
    }

    @Test
    @DisplayName("Desativar: conta já desativada no meio lança InvalidSessionException")
    void shouldRejectDeactivateWhenAlreadyInactive(CapturedOutput output) {
        activeUser();
        when(passwordEncoder.matches("SenhaAtual1", "hash")).thenReturn(true);
        when(userRepository.deactivate(Mockito.eq(user.getId()), any())).thenReturn(0);

        assertThatThrownBy(() -> service.deactivate(user.getId(),
                new DeleteAccountRequest("SenhaAtual1")))
                .isInstanceOf(InvalidSessionException.class);
        verify(sessionRevoker, never()).revokeAll(any());
        assertThat(output.getOut()).contains("durante a requisição (userId=" + user.getId() + ")")
                .doesNotContain("SenhaAtual1");
    }
}
