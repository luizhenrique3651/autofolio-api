package com.autofolio.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.autofolio.AbstractPostgresIT;
import com.autofolio.auth.PasswordResetMailer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Tag("integration")
@DisplayName("Testes de Integração - /users/me e /profile (Postgres via Testcontainers)")
class UserAccountIT extends AbstractPostgresIT {

    private static final String EMAIL = "vendedor@teste.com";
    private static final String PASSWORD = "SenhaAntiga1";
    private static final String NEW_PASSWORD = "SenhaNova123";
    private static final String SLUG = "vendedor-teste";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JwtDecoder jwtDecoder;

    @MockBean
    private PasswordResetMailer mailer;

    @BeforeEach
    void setUp() {
        reset(mailer);
        jdbc.update("DELETE FROM password_reset_tokens");
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM user_profiles");
        jdbc.update("DELETE FROM users");
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body)
            throws Exception {
        var builder = request.contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(builder);
    }

    private JsonNode register(String email, String slug) throws Exception {
        return json(send(post("/api/v1/auth/register"), null,
                Map.of("name", "Vendedor Teste", "email", email, "slug", slug,
                        "password", PASSWORD)).andReturn());
    }

    private int login(String password) throws Exception {
        return send(post("/api/v1/auth/login"), null,
                Map.of("email", EMAIL, "password", password)).andReturn().getResponse()
                .getStatus();
    }

    private int refresh(String refreshToken) throws Exception {
        return send(post("/api/v1/auth/refresh"), null, Map.of("refreshToken", refreshToken))
                .andReturn().getResponse().getStatus();
    }

    private MvcResult me(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/users/me")
                .header("Authorization", "Bearer " + token)).andReturn();
    }

    @Test
    @DisplayName("GET /users/me devolve usuário e perfil padrão, sem password")
    void shouldReturnMe() throws Exception {
        var auth = register(EMAIL, SLUG);

        var result = me(auth.get("accessToken").asText());

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var body = json(result);
        assertThat(body.get("email").asText()).isEqualTo(EMAIL);
        assertThat(body.get("slug").asText()).isEqualTo(SLUG);
        assertThat(body.get("profile").get("expYears").asInt()).isZero();
        assertThat(body.get("profile").get("specialties")).isEmpty();
        assertThat(body.toString()).doesNotContain("password").doesNotContain("$2");
    }

    @Test
    @DisplayName("PUT /profile persiste todos os campos (incl. especialidades) e o GET reflete")
    void shouldUpdateProfile() throws Exception {
        var token = register(EMAIL, SLUG).get("accessToken").asText();

        var result = send(put("/api/v1/profile"), token, Map.of("bio", "Vendo carros há anos",
                "whatsapp", "+5511999998888", "city", "São Paulo",
                "specialties", List.of("SUV", "Sedan"), "expYears", 7)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(result).get("specialties")).hasSize(2);
        var profile = json(me(token)).get("profile");
        assertThat(profile.get("bio").asText()).isEqualTo("Vendo carros há anos");
        assertThat(profile.get("whatsapp").asText()).isEqualTo("+5511999998888");
        assertThat(profile.get("city").asText()).isEqualTo("São Paulo");
        assertThat(profile.get("specialties").get(1).asText()).isEqualTo("Sedan");
        assertThat(profile.get("expYears").asInt()).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_profiles", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("PUT /profile: 400 para dados inválidos")
    void shouldRejectInvalidProfile() throws Exception {
        var token = register(EMAIL, SLUG).get("accessToken").asText();

        send(put("/api/v1/profile"), token, Map.of("whatsapp", "abc", "expYears", -1))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /slug: troca, devolve token com o slug novo e o antigo fica livre")
    void shouldChangeSlug() throws Exception {
        var token = register(EMAIL, SLUG).get("accessToken").asText();

        var result = send(patch("/api/v1/users/me/slug"), token, Map.of("slug", "novo-slug"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var newToken = json(result).get("accessToken").asText();
        assertThat(jwtDecoder.decode(newToken).getClaimAsString("slug")).isEqualTo("novo-slug");
        assertThat(json(me(token)).get("slug").asText()).isEqualTo("novo-slug");
        // O slug antigo foi liberado
        assertThat(register("outro@teste.com", SLUG).has("accessToken")).isTrue();
    }

    @Test
    @DisplayName("PATCH /slug: 409 se em uso por outro usuário, 200 se for o próprio slug")
    void shouldRejectSlugInUse() throws Exception {
        var token = register(EMAIL, SLUG).get("accessToken").asText();
        register("outro@teste.com", "outro-slug");

        assertThat(send(patch("/api/v1/users/me/slug"), token, Map.of("slug", "outro-slug"))
                .andReturn().getResponse().getStatus()).isEqualTo(409);
        assertThat(send(patch("/api/v1/users/me/slug"), token, Map.of("slug", SLUG))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(send(patch("/api/v1/users/me/slug"), token, Map.of("slug", "Inválido!"))
                .andReturn().getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("PATCH /password: 401 com a atual errada; com a certa troca e revoga os refresh")
    void shouldChangePassword() throws Exception {
        var auth = register(EMAIL, SLUG);
        var token = auth.get("accessToken").asText();
        var refreshToken = auth.get("refreshToken").asText();

        assertThat(send(patch("/api/v1/users/me/password"), token,
                Map.of("currentPassword", "Errada123", "newPassword", NEW_PASSWORD))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(login(PASSWORD)).isEqualTo(200);

        assertThat(send(patch("/api/v1/users/me/password"), token,
                Map.of("currentPassword", PASSWORD, "newPassword", "fraca"))
                .andReturn().getResponse().getStatus()).isEqualTo(400);

        assertThat(send(patch("/api/v1/users/me/password"), token,
                Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD))
                .andReturn().getResponse().getStatus()).isEqualTo(204);

        assertThat(login(PASSWORD)).isEqualTo(401);
        assertThat(login(NEW_PASSWORD)).isEqualTo(200);
        assertThat(refresh(refreshToken)).isEqualTo(401);
        var hash = jdbc.queryForObject("SELECT password_hash FROM users", String.class);
        assertThat(hash).startsWith("$2").doesNotContain(NEW_PASSWORD);
    }

    @Test
    @DisplayName("DELETE /users/me: 401 com senha errada; com a certa desativa (soft delete)")
    void shouldDeactivateAccount() throws Exception {
        var auth = register(EMAIL, SLUG);
        var token = auth.get("accessToken").asText();
        var refreshToken = auth.get("refreshToken").asText();

        assertThat(send(delete("/api/v1/users/me"), token, Map.of("password", "Errada123"))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT active FROM users", Boolean.class)).isTrue();

        assertThat(send(delete("/api/v1/users/me"), token, Map.of("password", PASSWORD))
                .andReturn().getResponse().getStatus()).isEqualTo(204);

        // Linha preservada, só inativa
        assertThat(jdbc.queryForObject("SELECT active FROM users", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_profiles", Integer.class))
                .isEqualTo(1);
        // Não loga, não renova token e o access token antigo não abre /users/me nem /profile
        assertThat(login(PASSWORD)).isEqualTo(401);
        assertThat(refresh(refreshToken)).isEqualTo(401);
        assertThat(me(token).getResponse().getStatus()).isEqualTo(401);
        assertThat(send(put("/api/v1/profile"), token, Map.of("bio", "x")).andReturn()
                .getResponse().getStatus()).isEqualTo(401);
        // Segundo DELETE com o mesmo token: 401
        assertThat(send(delete("/api/v1/users/me"), token, Map.of("password", PASSWORD))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("Conta desativada: login responde igual a senha errada, e-mail e slug seguem "
            + "reservados, forgot-password não envia e-mail")
    void shouldNotRevealDeactivatedAccount() throws Exception {
        var token = register(EMAIL, SLUG).get("accessToken").asText();
        send(delete("/api/v1/users/me"), token, Map.of("password", PASSWORD))
                .andReturn();

        var inactive = send(post("/api/v1/auth/login"), null,
                Map.of("email", EMAIL, "password", PASSWORD)).andReturn();
        var wrong = send(post("/api/v1/auth/login"), null,
                Map.of("email", EMAIL, "password", "Errada123")).andReturn();
        var unknown = send(post("/api/v1/auth/login"), null,
                Map.of("email", "ninguem@teste.com", "password", PASSWORD)).andReturn();
        for (var result : new MvcResult[] {inactive, wrong}) {
            assertThat(result.getResponse().getStatus()).isEqualTo(401);
            assertThat(json(result).get("detail").asText())
                    .isEqualTo(json(unknown).get("detail").asText());
        }

        // E-mail e slug continuam ocupados
        assertThat(send(post("/api/v1/auth/register"), null,
                Map.of("name", "Outro Nome", "email", EMAIL, "slug", "livre-slug",
                        "password", PASSWORD)).andReturn().getResponse().getStatus())
                .isEqualTo(409);
        assertThat(send(post("/api/v1/auth/register"), null,
                Map.of("name", "Outro Nome", "email", "novo@teste.com", "slug", SLUG,
                        "password", PASSWORD)).andReturn().getResponse().getStatus())
                .isEqualTo(409);

        // forgot-password responde 202 e não envia e-mail
        assertThat(send(post("/api/v1/auth/forgot-password"), null, Map.of("email", EMAIL))
                .andReturn().getResponse().getStatus()).isEqualTo(202);
        verify(mailer, after(700).never()).send(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Sem token todos os endpoints respondem 401")
    void shouldRequireToken() throws Exception {
        assertThat(mockMvc.perform(get("/api/v1/users/me")).andReturn().getResponse()
                .getStatus()).isEqualTo(401);
        assertThat(send(put("/api/v1/profile"), null, Map.of()).andReturn().getResponse()
                .getStatus()).isEqualTo(401);
    }
}
