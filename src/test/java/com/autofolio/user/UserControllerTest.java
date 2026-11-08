package com.autofolio.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autofolio.config.SecurityConfig;
import com.autofolio.shared.exception.DataConflictException;
import com.autofolio.shared.exception.GlobalExceptionHandler;
import com.autofolio.shared.exception.InvalidCredentialsException;
import com.autofolio.shared.exception.InvalidSessionException;
import com.autofolio.user.dto.ProfileResponse;
import com.autofolio.user.dto.SlugResponse;
import com.autofolio.user.dto.UserMeResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest({UserController.class, ProfileController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("Testes de Controller - /users/me e /profile")
class UserControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;
    @MockBean
    private ProfileService profileService;
    @MockBean
    private JwtDecoder jwtDecoder;

    private static RequestPostProcessor auth() {
        return jwt().jwt(token -> token.subject(USER_ID.toString()));
    }

    @Test
    @DisplayName("GET /users/me: 200 com usuário e perfil, sem password")
    void shouldReturnMe() throws Exception {
        var profile = new ProfileResponse("Bio", "11999998888", "SP", null, List.of("SUV"), 2);
        when(userService.me(USER_ID)).thenReturn(new UserMeResponse(USER_ID, "Luiz",
                "luiz@teste.com", "luiz", OffsetDateTime.now(), profile));

        mockMvc.perform(get("/api/v1/users/me").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.email").value("luiz@teste.com"))
                .andExpect(jsonPath("$.profile.specialties[0]").value("SUV"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("Sem token: 401 ProblemDetail em todos os endpoints")
    void shouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mockMvc.perform(put("/api/v1/profile").contentType(JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/v1/users/me/slug").contentType(JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/v1/users/me/password").contentType(JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/users/me").contentType(JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(userService, profileService);
    }

    @Test
    @DisplayName("Conta desativada: 401 com a mensagem genérica de token inválido")
    void shouldReturn401ForInactiveAccount() throws Exception {
        when(userService.me(USER_ID)).thenThrow(new InvalidSessionException());

        mockMvc.perform(get("/api/v1/users/me").with(auth()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail")
                        .value("Autenticação necessária ou token inválido."));
    }

    @Test
    @DisplayName("PUT /profile: 200 com o perfil atualizado")
    void shouldUpdateProfile() throws Exception {
        when(profileService.update(any(), any())).thenReturn(
                new ProfileResponse("Bio", "11999998888", "SP", null, List.of("SUV"), 4));

        mockMvc.perform(put("/api/v1/profile").with(auth()).contentType(JSON)
                        .content("""
                                {"bio":"Bio","whatsapp":"11999998888","city":"SP",
                                 "specialties":["SUV"],"expYears":4}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("Bio"))
                .andExpect(jsonPath("$.expYears").value(4));
        verify(profileService).update(any(UUID.class), any());
    }

    @ParameterizedTest
    @DisplayName("PUT /profile: 400 com errors para dados inválidos")
    @ValueSource(strings = {
        "{\"whatsapp\":\"abc\"}",
        "{\"whatsapp\":\"123\"}",
        "{\"expYears\":-1}",
        "{\"city\":\"%s\"}",
        "{\"bio\":\"%s\"}",
        "{\"specialties\":[\"\"]}",
        "{\"specialties\":[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\",\"g\",\"h\",\"i\",\"j\",\"k\"]}"
    })
    void shouldRejectInvalidProfile(String template) throws Exception {
        var body = template.contains("%s")
                ? template.formatted("x".repeat(template.contains("city") ? 101 : 2001))
                : template;

        mockMvc.perform(put("/api/v1/profile").with(auth()).contentType(JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
        verifyNoInteractions(profileService);
    }

    @Test
    @DisplayName("PATCH /slug: 200 com slug e token novo")
    void shouldUpdateSlug() throws Exception {
        when(userService.updateSlug(any(), any()))
                .thenReturn(new SlugResponse("novo", "jwt", 3600));

        mockMvc.perform(patch("/api/v1/users/me/slug").with(auth()).contentType(JSON)
                        .content("{\"slug\":\"novo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("novo"))
                .andExpect(jsonPath("$.accessToken").value("jwt"));
    }

    @ParameterizedTest
    @DisplayName("PATCH /slug: 400 para slug fora do formato")
    @ValueSource(strings = {"{\"slug\":\"Com Espaço\"}", "{\"slug\":\"MAIUSCULA\"}",
        "{\"slug\":\"\"}", "{}", "{\"slug\":\"a_b\"}"})
    void shouldRejectInvalidSlug(String body) throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/slug").with(auth()).contentType(JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("PATCH /slug: 400 (e não 500) para slug de 256 caracteres")
    void shouldRejectTooLongSlug() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/slug").with(auth()).contentType(JSON)
                        .content("{\"slug\":\"" + "a".repeat(256) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").exists());
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("PATCH /slug: 409 quando em uso")
    void shouldReturn409WhenSlugInUse() throws Exception {
        when(userService.updateSlug(any(), any()))
                .thenThrow(new DataConflictException("Slug já está em uso por outro usuário."));

        mockMvc.perform(patch("/api/v1/users/me/slug").with(auth()).contentType(JSON)
                        .content("{\"slug\":\"ocupado\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    @DisplayName("PATCH /password: 204 com a senha atual correta")
    void shouldChangePassword() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/password").with(auth()).contentType(JSON)
                        .content("{\"currentPassword\":\"SenhaAtual1\","
                                + "\"newPassword\":\"SenhaNova123\"}"))
                .andExpect(status().isNoContent());
        verify(userService).changePassword(any(UUID.class), any());
    }

    @Test
    @DisplayName("PATCH /password: 401 quando a senha atual está incorreta")
    void shouldReturn401WhenCurrentPasswordWrong() throws Exception {
        doThrow(new InvalidCredentialsException()).when(userService)
                .changePassword(any(), any());

        mockMvc.perform(patch("/api/v1/users/me/password").with(auth()).contentType(JSON)
                        .content("{\"currentPassword\":\"errada\","
                                + "\"newPassword\":\"SenhaNova123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciais inválidas."));
    }

    @ParameterizedTest
    @DisplayName("PATCH /password: 400 para nova senha fraca ou campos ausentes")
    @ValueSource(strings = {
        "{\"currentPassword\":\"x\",\"newPassword\":\"fraca\"}",
        "{\"currentPassword\":\"x\",\"newPassword\":\"semnumeroMaiuscula\"}",
        "{\"newPassword\":\"SenhaNova123\"}",
        "{\"currentPassword\":\"x\"}"})
    void shouldRejectInvalidPasswordChange(String body) throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/password").with(auth()).contentType(JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("DELETE /users/me: 204 com a senha confirmada")
    void shouldDeactivate() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me").with(auth()).contentType(JSON)
                        .content("{\"password\":\"SenhaAtual1\"}"))
                .andExpect(status().isNoContent());
        verify(userService).deactivate(any(UUID.class), any());
    }

    @Test
    @DisplayName("DELETE /users/me: 401 com senha incorreta e 400 sem senha")
    void shouldRejectDeactivate() throws Exception {
        doThrow(new InvalidCredentialsException()).when(userService).deactivate(any(), any());

        mockMvc.perform(delete("/api/v1/users/me").with(auth()).contentType(JSON)
                        .content("{\"password\":\"errada\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/users/me").with(auth()).contentType(JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
