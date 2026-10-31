package com.autofolio.auth;

import com.autofolio.auth.dto.LoginRequest;
import com.autofolio.auth.dto.RegisterRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Testcontainers
@Tag("integration")
@DisplayName("Testes de Integração - AuthController (Postgres via Testcontainers)")
class AuthControllerIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static final String EMAIL = "vendedor@teste.com";
    private static final String PASSWORD = "password123";
    private static final String SLUG = "vendedor-teste";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM user_profiles");
        jdbc.update("DELETE FROM users");
    }

    private MvcResult register() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("Vendedor Teste", EMAIL, SLUG, PASSWORD))))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andReturn();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Registro: 201 com JWT válido e refresh token persistido")
    void registerReturnsRealTokens() throws Exception {
        var json = body(register());

        assertThat(jwtDecoder.decode(json.get("accessToken").asText()).getSubject())
                .isEqualTo(json.get("user").get("id").asText());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE token = ?", Integer.class,
                json.get("refreshToken").asText())).isEqualTo(1);
    }

    @Test
    @DisplayName("Registro: 400 com dados inválidos e 409 com e-mail duplicado")
    void registerErrors() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("", "x", "Slug Ruim", "s"))))
                .andExpect(status().isBadRequest());
        register();
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("Outro", EMAIL, "outro", PASSWORD))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Login: 200 com tokens, JWT HS256 com claims corretas e exp = iat + 3600")
    void loginSuccess() throws Exception {
        var userId = body(register()).get("user").get("id").asText();

        var result = login(EMAIL, PASSWORD);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var json = body(result);
        assertThat(json.get("expiresIn").asInt()).isEqualTo(3600);
        assertThat(json.get("refreshToken").asText()).isNotBlank();

        Jwt jwt = jwtDecoder.decode(json.get("accessToken").asText());
        assertThat(jwt.getHeaders().get("alg")).isEqualTo("HS256");
        assertThat(jwt.getSubject()).isEqualTo(userId);
        assertThat(jwt.getClaimAsString("slug")).isEqualTo(SLUG);
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofSeconds(3600));
    }

    @Test
    @DisplayName("Login: refresh token persistido com ~30 dias, não revogado, vinculado ao usuário")
    void loginPersistsRefreshToken() throws Exception {
        var userId = body(register()).get("user").get("id").asText();
        var refresh = body(login(EMAIL, PASSWORD)).get("refreshToken").asText();

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT user_id::text AS user_id, expires_at, revoked FROM refresh_tokens WHERE token = ?", refresh);
        assertThat(row.get("user_id")).isEqualTo(userId);
        assertThat((Boolean) row.get("revoked")).isFalse();
        var expiresAt = ((java.sql.Timestamp) row.get("expires_at")).toInstant().atOffset(java.time.ZoneOffset.UTC);
        assertThat(Duration.between(OffsetDateTime.now(), expiresAt).toDays()).isBetween(29L, 30L);
    }

    @Test
    @DisplayName("Login: dois logins geram dois refresh tokens distintos (+1 do registro)")
    void multipleLoginsCreateMultipleRefreshTokens() throws Exception {
        register();
        var first = body(login(EMAIL, PASSWORD)).get("refreshToken").asText();
        var second = body(login(EMAIL, PASSWORD)).get("refreshToken").asText();

        assertThat(first).isNotEqualTo(second);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens", Integer.class)).isEqualTo(3);
    }

    @Test
    @DisplayName("Login: e-mail inexistente e senha errada retornam resposta idêntica (401 genérico)")
    void invalidCredentialsAreIndistinguishable() throws Exception {
        register();
        var wrongPassword = login(EMAIL, "senha-errada-1");
        var unknownEmail = login("naoexiste@teste.com", PASSWORD);

        assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknownEmail.getResponse().getStatus()).isEqualTo(401);
        var a = body(wrongPassword);
        var b = body(unknownEmail);
        assertThat(a.get("detail")).isEqualTo(b.get("detail"));
        assertThat(a.get("title")).isEqualTo(b.get("title"));
        assertThat(a.get("detail").asText()).doesNotContain(EMAIL).doesNotContainIgnoringCase("senha");
        assertThat(wrongPassword.getResponse().getContentAsString()).doesNotContain("$2");
        // falha de login não cria refresh token (só o do registro)
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Login: 400 com body inválido")
    void loginInvalidBody() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nao-e-email\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Login: 400 com JSON malformado")
    void loginMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{malformado"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Login: Bearer inválido em /auth/** não gera 401 por token")
    void invalidBearerOnAuthRouteIsIgnored() throws Exception {
        register();
        mockMvc.perform(post("/api/v1/auth/login").header("Authorization", "Bearer lixo.invalido.token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(EMAIL, PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    @DisplayName("Rota protegida: 401 sem token, com Bearer inválido; 200/404 (não 401) com JWT válido")
    void protectedRoute() throws Exception {
        mockMvc.perform(get("/api/v1/vehicles")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/vehicles").header("Authorization", "Bearer lixo"))
                .andExpect(status().isUnauthorized());

        register();
        var token = body(login(EMAIL, PASSWORD)).get("accessToken").asText();
        var status = mockMvc.perform(get("/api/v1/vehicles").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();
        assertThat(status).isNotEqualTo(401).isNotEqualTo(403);
    }
}
