package com.autofolio.auth;

import com.autofolio.AbstractPostgresIT;
import com.autofolio.auth.dto.ForgotPasswordRequest;
import com.autofolio.auth.dto.LoginRequest;
import com.autofolio.auth.dto.RefreshRequest;
import com.autofolio.auth.dto.RegisterRequest;
import com.autofolio.shared.security.TokenHasher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Tag("integration")
@DisplayName("Testes de Integração - AuthController (Postgres via Testcontainers)")
class AuthControllerIT extends AbstractPostgresIT {

    private static final String EMAIL = "vendedor@teste.com";
    private static final String PASSWORD = "Password123";
    private static final String SLUG = "vendedor-teste";

    // language=TEXT
    private static final String MALFORMED_JSON = "{malformado";
    // language=TEXT
    private static final String NOT_JSON = "not json";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtDecoder jwtDecoder;

    // Nenhum e-mail real é enviado nos testes
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
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
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
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("Outro", "outro@teste.com", SLUG, PASSWORD))))
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
        var expiresAt = ((Timestamp) row.get("expires_at")).toInstant().atOffset(ZoneOffset.UTC);
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
        assertThat(wrongPassword.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain("$2");
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
        var result = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(MALFORMED_JSON))
                .andReturn();

        assertProblem(result, 400, "Requisição Inválida", "bad-request");
        assertThat(body(result).get("detail").asText()).isEqualTo("Corpo da requisição ausente ou malformado.");
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain("Jackson", "Unexpected", "line:");
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

    private MvcResult refresh(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(token))))
                .andReturn();
    }

    @Test
    @DisplayName("Refresh: 200 com novo access token válido; refresh token reutilizável e inalterado")
    void refreshSuccessAndReusable() throws Exception {
        var userId = body(register()).get("user").get("id").asText();
        var rt = body(login(EMAIL, PASSWORD)).get("refreshToken").asText();
        var before = jdbc.queryForObject("SELECT count(*) FROM refresh_tokens", Integer.class);

        for (int i = 0; i < 2; i++) {
            var result = refresh(rt);
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            var json = body(result);
            assertThat(json.get("expiresIn").asInt()).isEqualTo(3600);
            assertThat(jwtDecoder.decode(json.get("accessToken").asText()).getSubject()).isEqualTo(userId);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens", Integer.class)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE token = ?", Integer.class, rt)).isEqualTo(1);
    }

    @Test
    @DisplayName("Refresh: 401 para token inexistente")
    void refreshUnknownToken() throws Exception {
        register();
        assertThat(refresh("nao-existe").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("Refresh: 401 para token revogado, que permanece na tabela")
    void refreshRevokedToken() throws Exception {
        register();
        var revoked = body(login(EMAIL, PASSWORD)).get("refreshToken").asText();
        jdbc.update("UPDATE refresh_tokens SET revoked = true WHERE token = ?", revoked);

        assertThat(refresh(revoked).getResponse().getStatus()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE token = ?", Integer.class, revoked)).isEqualTo(1);
    }

    @Test
    @DisplayName("Refresh: 401 para token expirado, que é removido da tabela")
    void refreshExpiredToken() throws Exception {
        register();
        var expired = body(login(EMAIL, PASSWORD)).get("refreshToken").asText();
        jdbc.update("UPDATE refresh_tokens SET expires_at = now() - interval '1 minute' WHERE token = ?", expired);

        var result = refresh(expired);
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("Refresh token inválido ou expirado.");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE token = ?", Integer.class, expired)).isZero();
    }

    @Test
    @DisplayName("Refresh: access token novo tem claims corretas e funciona em rota protegida")
    void refreshTokenClaimsAndProtectedRoute() throws Exception {
        var userId = body(register()).get("user").get("id").asText();
        var rt = body(login(EMAIL, PASSWORD)).get("refreshToken").asText();
        var at = body(refresh(rt)).get("accessToken").asText();
        Jwt jwt = jwtDecoder.decode(at);
        assertThat(jwt.getSubject()).isEqualTo(userId);
        assertThat(jwt.<String>getClaim("slug")).isEqualTo(SLUG);
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(1));
        var st = mockMvc.perform(get("/api/v1/vehicles").header("Authorization", "Bearer " + at))
                .andReturn().getResponse().getStatus();
        assertThat(st).isNotEqualTo(401).isNotEqualTo(403);
    }

    @Test
    @DisplayName("Refresh: 401 idêntico (corpo, content-type) para inexistente, revogado e expirado")
    void refreshInvalidTokensHaveSameResponse() throws Exception {
        register();
        var revoked = body(login(EMAIL, PASSWORD)).get("refreshToken").asText();
        var expired = body(login(EMAIL, PASSWORD)).get("refreshToken").asText();
        jdbc.update("UPDATE refresh_tokens SET revoked = true WHERE token = ?", revoked);
        jdbc.update("UPDATE refresh_tokens SET expires_at = now() - interval '1 second' WHERE token = ?", expired);
        var normalized = new HashSet<String>();
        for (var t : List.of("nao-existe", revoked, expired)) {
            var r = refresh(t);
            assertThat(r.getResponse().getStatus()).isEqualTo(401);
            assertThat(r.getResponse().getContentType()).contains("application/problem+json");
            var j = (ObjectNode) body(r);
            j.remove("timestamp");
            j.remove("requestId"); // único por requisição, de propósito
            normalized.add(j.toString());
        }
        assertThat(normalized).hasSize(1);
    }

    @Test
    @DisplayName("Refresh: expirado e revogado também é removido; não afeta outros tokens")
    void refreshExpiredOnlyDeletesItself() throws Exception {
        register();
        var a = body(login(EMAIL, PASSWORD)).get("refreshToken").asText();
        var b = body(login(EMAIL, PASSWORD)).get("refreshToken").asText();
        jdbc.update("UPDATE refresh_tokens SET expires_at = now() - interval '1 minute', revoked = true WHERE token = ?", a);
        assertThat(refresh(a).getResponse().getStatus()).isEqualTo(401);
        assertThat(refresh(a).getResponse().getStatus()).isEqualTo(401);
        assertThat(refresh(b).getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE token = ?", Integer.class, a)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE token = ?", Integer.class, b)).isEqualTo(1);
    }

    @Test
    @DisplayName("Refresh: requisições concorrentes no mesmo token expirado → todas 401, nenhuma 500")
    void refreshConcurrentExpiredNever500() throws Exception {
        var userId = UUID.fromString(body(register()).get("user").get("id").asText());
        for (int round = 0; round < 15; round++) {
            // Insere direto no banco: evita o custo de BCrypt de um login por rodada
            var rt = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO refresh_tokens (user_id, token, expires_at) VALUES (?, ?, now() - interval '1 minute')", userId, rt);
            var go = new CountDownLatch(1);
            try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
                try {
                    List<Future<Integer>> futures = new ArrayList<>();
                    for (int i = 0; i < 4; i++) {
                        futures.add(pool.submit(() -> {
                            go.await();
                            return refresh(rt).getResponse().getStatus();
                        }));
                    }
                    go.countDown();
                    for (var future : futures) {
                        assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo(401);
                    }
                } finally {
                    // Se o get estourar o timeout, close() esperaria as threads travadas para sempre
                    pool.shutdownNow();
                }
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE token = ?", Integer.class, rt)).isZero();
        }
    }

    @Test
    @DisplayName("Refresh: 400 para campo ausente/null/branco/longo demais, JSON malformado e sem body")
    void refreshBadRequests() throws Exception {
        for (var json : List.of("{}", "{\"refreshToken\":null}", "{\"refreshToken\":\"\"}", "{\"refreshToken\":\"   \"}")) {
            var r = mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
            assertThat(r.getResponse().getStatus()).as(json).isEqualTo(400);
            assertThat(r.getResponse().getContentType()).contains("application/problem+json");
        }
        for (var json : List.of(MALFORMED_JSON, NOT_JSON, "[]", "{\"refreshToken\":{\"a\":1}}")) {
            assertThat(mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(json))
                    .andReturn().getResponse().getStatus()).as(json).isEqualTo(400);
        }
        assertThat(refresh("x".repeat(256)).getResponse().getStatus()).as("maior que a coluna").isEqualTo(400);
        assertThat(mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getStatus()).as("sem body").isEqualTo(400);
    }

    @Test
    @DisplayName("Refresh: limite de 255 chars -> 401 (aceito, inexistente); 256 -> 400 com errors referenciando refreshToken")
    void refreshTokenLengthBoundary() throws Exception {
        assertProblem(refresh("x".repeat(255)), 401, "Não Autorizado", "unauthorized");
        var r = refresh("x".repeat(256));
        assertProblem(r, 400, "Requisição Inválida", "bad-request");
        assertThat(objectMapper.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("errors").asText())
                .startsWith("refreshToken:");
    }

    private void assertProblem(MvcResult r, int status, String title, String typeSlug) throws Exception {
        var raw = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(r.getResponse().getStatus()).as(raw).isEqualTo(status);
        assertThat(r.getResponse().getContentType()).contains("application/problem+json");
        var j = objectMapper.readTree(raw);
        assertThat(j.get("status").asInt()).isEqualTo(status);
        assertThat(j.get("title").asText()).isEqualTo(title);
        assertThat(j.get("type").asText()).isEqualTo("https://autofolio.com/errors/" + typeSlug);
        assertThat(j.get("timestamp").asText()).isNotBlank();
        assertThat(raw.split("\"timestamp\"", -1).length - 1).as("timestamp duplicado: " + raw).isEqualTo(1);
    }

    @Test
    @DisplayName("Handler: 415, 405, 404 e 406 em problem+json padronizado")
    void springMvcErrorsArePadronized() throws Exception {
        assertProblem(mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.TEXT_PLAIN).content("x")).andReturn(),
                415, "Tipo de Mídia Não Suportado", "unsupported-media-type");
        assertProblem(mockMvc.perform(get("/api/v1/auth/refresh")).andReturn(),
                405, "Método Não Permitido", "method-not-allowed");
        assertProblem(mockMvc.perform(get("/api/v1/auth/nao-existe")).andReturn(),
                404, "Recurso Não Encontrado", "not-found");
        register();
        var rt = body(login(EMAIL, PASSWORD)).get("refreshToken").asText();
        assertProblem(mockMvc.perform(post("/api/v1/auth/refresh").accept(MediaType.APPLICATION_XML)
                .contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"" + rt + "\"}")).andReturn(),
                406, "Formato Não Aceito", "not-acceptable");
        var at = body(login(EMAIL, PASSWORD)).get("accessToken").asText();
        assertProblem(mockMvc.perform(get("/api/v1/rota-inexistente").header("Authorization", "Bearer " + at)).andReturn(),
                404, "Recurso Não Encontrado", "not-found");
    }

    @Test
    @DisplayName("Handler: formato dos 400, 409, 401s e Bearer malformado")
    void errorShapesArePadronized() throws Exception {
        var v = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();
        assertProblem(v, 400, "Requisição Inválida", "bad-request");
        assertThat(body(v).get("detail").asText()).isEqualTo("Falha na validação dos campos.");
        assertThat(body(v).get("errors").asText()).contains("email:");
        register();
        var c = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest("Xxxx", EMAIL, "outro-slug", PASSWORD)))).andReturn();
        assertProblem(c, 409, "Conflito de Dados", "conflict");
        assertProblem(login(EMAIL, "errada123"), 401, "Não Autorizado", "unauthorized");
        assertProblem(refresh("nao-existe"), 401, "Não Autorizado", "unauthorized");
        var rs = mockMvc.perform(get("/api/v1/vehicles").header("Authorization", "Bearer lixo")).andReturn();
        assertProblem(rs, 401, "Não Autorizado", "unauthorized");
        assertThat(rs.getResponse().getHeader("WWW-Authenticate")).startsWith("Bearer");
        var bad = mockMvc.perform(get("/api/v1/vehicles").header("Authorization", "Bearer a b c")).andReturn();
        // O resolver padrão trata header malformado como invalid_token (401); invalid_request (400) não é alcançável aqui
        assertProblem(bad, 401, "Não Autorizado", "unauthorized");
        assertThat(bad.getResponse().getHeader("WWW-Authenticate")).contains("invalid_token");
    }

    @Test
    @DisplayName("Refresh: Bearer inválido na rota é ignorado; token com espaços ou case alterado não existe")
    void refreshIgnoresBearerAndMatchesExactly() throws Exception {
        register();
        var rt = body(login(EMAIL, PASSWORD)).get("refreshToken").asText();
        var r = mockMvc.perform(post("/api/v1/auth/refresh").header("Authorization", "Bearer lixo")
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(new RefreshRequest(rt)))).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(refresh(rt.toUpperCase()).getResponse().getStatus()).isEqualTo(401);
        assertThat(refresh(" " + rt).getResponse().getStatus()).isEqualTo(401);
        assertThat(refresh("x".repeat(255)).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("Refresh: 200 só com accessToken/expiresIn; 401 sem stack nem SQL")
    void refreshDoesNotLeak() throws Exception {
        register();
        var rt = body(login(EMAIL, PASSWORD)).get("refreshToken").asText();
        var ok = body(refresh(rt));
        assertThat(ok.fieldNames()).toIterable().containsExactlyInAnyOrder("accessToken", "expiresIn");
        var err = refresh("nao-existe").getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(err).doesNotContain("Exception", "SQL", "com.autofolio", "nao-existe");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"Bearer lixo", "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.assinaturaerrada"})
    @DisplayName("Resource server: 401 com WWW-Authenticate e ProblemDetail (sem token, inválido, assinatura errada)")
    void resourceServer401(String authorization) throws Exception {
        var request = get("/api/v1/vehicles");
        if (authorization != null) {
            request = request.header("Authorization", authorization);
        }
        var result = mockMvc.perform(request).andReturn();

        assertProblem(result, 401, "Não Autorizado", "unauthorized");
        assertThat(result.getResponse().getHeader("WWW-Authenticate")).startsWith("Bearer");
    }

    private MvcResult forgotPassword(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ForgotPasswordRequest(email))))
                .andReturn();
    }

    @Test
    @DisplayName("Forgot-password: e-mail existente grava token (1h, used=false) e envia o e-mail")
    void forgotPasswordExistingEmail() throws Exception {
        register();

        var result = forgotPassword(EMAIL);

        assertThat(result.getResponse().getStatus()).isEqualTo(202);
        assertThat(result.getResponse().getContentAsString()).isEmpty();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT count(*) FROM password_reset_tokens", Integer.class)).isEqualTo(1));
        var row = jdbc.queryForMap("SELECT token_hash, used, expires_at FROM password_reset_tokens");
        assertThat(row.get("used")).isEqualTo(false);
        var expiresAt = ((Timestamp) row.get("expires_at")).toInstant();
        assertThat(Duration.between(Instant.now(), expiresAt)).isBetween(Duration.ofMinutes(58), Duration.ofMinutes(61));
        var rawToken = ArgumentCaptor.forClass(String.class);
        verify(mailer, timeout(5000)).send(eq(EMAIL), eq("Vendedor Teste"), rawToken.capture(), any());
        assertThat(row.get("token_hash")).isEqualTo(TokenHasher.sha256Hex(rawToken.getValue())).isNotEqualTo(rawToken.getValue());
    }

    @Test
    @DisplayName("Forgot-password: e-mail inexistente também retorna 202, sem token nem e-mail")
    void forgotPasswordUnknownEmail() throws Exception {
        var result = forgotPassword("naoexiste@teste.com");

        assertThat(result.getResponse().getStatus()).isEqualTo(202);
        assertThat(result.getResponse().getContentAsString()).isEmpty();
        verify(mailer, after(500).never()).send(anyString(), anyString(), anyString(), any());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM password_reset_tokens", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Forgot-password: falha de SMTP não vira 500")
    void forgotPasswordSmtpFailure() throws Exception {
        register();
        doThrow(new MailSendException("smtp down"))
                .when(mailer).send(anyString(), anyString(), anyString(), any());

        var result = forgotPassword(EMAIL);

        assertThat(result.getResponse().getStatus()).isEqualTo(202);
        verify(mailer, timeout(5000)).send(anyString(), anyString(), anyString(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "nao-e-email"})
    @DisplayName("Forgot-password: e-mail em branco ou malformado retorna 400")
    void forgotPasswordInvalidEmail(String email) throws Exception {
        assertThat(forgotPassword(email).getResponse().getStatus()).isEqualTo(400);
    }
}
