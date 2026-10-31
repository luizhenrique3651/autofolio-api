package com.autofolio.auth;

import com.autofolio.AbstractPostgresIT;
import com.autofolio.auth.dto.LoginRequest;
import com.autofolio.auth.dto.RefreshRequest;
import com.autofolio.auth.dto.RegisterRequest;
import com.autofolio.auth.dto.ResetPasswordRequest;
import com.autofolio.shared.security.TokenHasher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Tag("integration")
@DisplayName("Testes de Integração - POST /auth/reset-password (Postgres via Testcontainers)")
class ResetPasswordIT extends AbstractPostgresIT {

    private static final String EMAIL = "vendedor@teste.com";
    private static final String OLD_PASSWORD = "SenhaAntiga1";
    private static final String NEW_PASSWORD = "SenhaNova123";
    private static final String INVALID_MESSAGE = "Token de redefinição inválido, expirado ou já utilizado.";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder passwordEncoder;

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
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode register() throws Exception {
        return json(mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest("Vendedor Teste", EMAIL, "vendedor-teste", OLD_PASSWORD))))
                .andReturn());
    }

    private MvcResult login(String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(EMAIL, password)))).andReturn();
    }

    private MvcResult refresh(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshRequest(token)))).andReturn();
    }

    private MvcResult doReset(String token, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ResetPasswordRequest(token, password)))).andReturn();
    }

    /** Pede o reset e devolve o token cru capturado do e-mail. */
    private String requestToken() throws Exception {
        reset(mailer);
        mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + EMAIL + "\"}")).andReturn();
        var captor = ArgumentCaptor.forClass(String.class);
        verify(mailer, timeout(5000)).send(eq(EMAIL), anyString(), captor.capture(), any());
        return captor.getValue();
    }

    @Test
    @DisplayName("200: redefine a senha, marca o token como usado e revoga todos os refresh tokens")
    void resetsPasswordAndRevokesRefreshTokens() throws Exception {
        var registered = register();
        var refreshFromLogin = json(login(OLD_PASSWORD)).get("refreshToken").asText();
        var token = requestToken();

        var result = doReset(token, NEW_PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).isEmpty();
        assertThat(login(OLD_PASSWORD).getResponse().getStatus()).isEqualTo(401);
        assertThat(login(NEW_PASSWORD).getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT used FROM password_reset_tokens WHERE token_hash = ?", Boolean.class,
                TokenHasher.sha256Hex(token))).isTrue();
        // Os refresh tokens anteriores (registro e login) foram revogados; o do login novo continua válido
        assertThat(refresh(registered.get("refreshToken").asText()).getResponse().getStatus()).isEqualTo(401);
        assertThat(refresh(refreshFromLogin).getResponse().getStatus()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE revoked = false", Integer.class)).isEqualTo(1);
        // BCrypt força 12
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users", String.class)).startsWith("$2a$12$");
    }

    @Test
    @DisplayName("400 idêntico para token inexistente, já utilizado e expirado")
    void invalidExpiredAndUsedTokensAreIndistinguishable() throws Exception {
        register();
        var used = requestToken();
        assertThat(doReset(used, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(200);
        var expired = requestToken();
        jdbc.update("UPDATE password_reset_tokens SET expires_at = now() - interval '1 minute' WHERE token_hash = ?",
                TokenHasher.sha256Hex(expired));

        var bodies = new ArrayList<JsonNode>();
        for (var token : List.of("nao-existe", used, expired)) {
            var result = doReset(token, "OutraSenha123");
            assertThat(result.getResponse().getStatus()).as(token).isEqualTo(400);
            assertThat(result.getResponse().getContentType()).contains("application/problem+json");
            bodies.add(json(result));
        }
        for (var body : bodies) {
            assertThat(body.get("detail").asText()).isEqualTo(INVALID_MESSAGE);
            assertThat(body.get("title")).isEqualTo(bodies.getFirst().get("title"));
            assertThat(body.get("type")).isEqualTo(bodies.getFirst().get("type"));
        }
        // Nenhuma das tentativas alterou a senha
        assertThat(passwordEncoder.matches(NEW_PASSWORD, jdbc.queryForObject("SELECT password_hash FROM users", String.class))).isTrue();
    }

    @Test
    @DisplayName("400 para senha fora da política, sem consumir o token")
    void weakPasswordDoesNotConsumeToken() throws Exception {
        register();
        var token = requestToken();

        for (var weak : List.of("curta1A", "semmaiuscula1", "SemNumeroAqui")) {
            assertThat(doReset(token, weak).getResponse().getStatus()).as(weak).isEqualTo(400);
        }

        assertThat(jdbc.queryForObject("SELECT used FROM password_reset_tokens", Boolean.class)).isFalse();
        assertThat(doReset(token, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Banco guarda só o hash SHA-256 do token (o token cru nunca é persistido)")
    void storesOnlyTokenHash() throws Exception {
        register();
        var token = requestToken();

        var stored = jdbc.queryForList("SELECT token_hash FROM password_reset_tokens", String.class);

        assertThat(stored).containsExactly(TokenHasher.sha256Hex(token));
        assertThat(stored.getFirst()).hasSize(64).isNotEqualTo(token);
    }

    @Test
    @DisplayName("Concorrência: dois resets simultâneos com o mesmo token → um 200 e um 400 (sem 500)")
    void concurrentResetsConsumeTokenOnce() throws Exception {
        register();
        var token = requestToken();
        int threads = 2;
        var start = new CountDownLatch(1);
        var statuses = new ArrayList<Integer>();
        try (var executor = Executors.newFixedThreadPool(threads)) {
            var futures = new ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < threads; i++) {
                var password = "Concorrente" + i + "A";
                futures.add(executor.submit(() -> {
                    start.await();
                    return doReset(token, password).getResponse().getStatus();
                }));
            }
            start.countDown();
            for (var future : futures) {
                statuses.add(future.get());
            }
        }

        assertThat(statuses).containsExactlyInAnyOrder(200, 400);
    }

    @Test
    @DisplayName("Novo forgot-password invalida o token anterior e limpa tokens expirados/usados")
    void newRequestInvalidatesPreviousAndCleansUp() throws Exception {
        register();
        var userId = jdbc.queryForObject("SELECT id FROM users", UUID.class);
        // Token expirado e outro já usado, inseridos direto: devem ser removidos no próximo pedido
        jdbc.update("INSERT INTO password_reset_tokens (user_id, token_hash, expires_at, used) VALUES (?, 'h-expirado', now() - interval '1 hour', false)", userId);
        jdbc.update("INSERT INTO password_reset_tokens (user_id, token_hash, expires_at, used) VALUES (?, 'h-usado', now() + interval '1 hour', true)", userId);
        var first = requestToken();

        var second = requestToken();

        assertThat(first).isNotEqualTo(second);
        await().untilAsserted(() -> assertThat(jdbc.queryForList("SELECT token_hash FROM password_reset_tokens WHERE used = false", String.class))
                .containsExactly(TokenHasher.sha256Hex(second)));
        assertThat(jdbc.queryForList("SELECT token_hash FROM password_reset_tokens", String.class))
                .doesNotContain("h-expirado", "h-usado");
        assertThat(doReset(first, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(400);
        assertThat(doReset(second, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Register aplica a mesma política: 400 sem maiúscula ou sem número")
    void registerEnforcesPasswordPolicy() throws Exception {
        for (var weak : List.of("semmaiuscula1", "SemNumeroAqui", "Curta1A")) {
            var result = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new RegisterRequest("Vendedor Teste", EMAIL, "vendedor-teste", weak)))).andReturn();
            assertThat(result.getResponse().getStatus()).as(weak).isEqualTo(400);
        }
    }

    @Test
    @DisplayName("X-Request-Id: aceita o do cliente, gera quando inválido/ausente e devolve no header")
    void requestIdHeader() throws Exception {
        var echoed = mockMvc.perform(post("/api/v1/auth/reset-password").header("X-Request-Id", "meu-id-1")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();
        var generated = mockMvc.perform(post("/api/v1/auth/reset-password").header("X-Request-Id", "inválido\nx")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();

        assertThat(echoed.getResponse().getHeader("X-Request-Id")).isEqualTo("meu-id-1");
        assertThat(generated.getResponse().getHeader("X-Request-Id")).matches("[0-9a-f-]{36}");
    }

    @Test
    @DisplayName("ProblemDetail traz requestId igual ao header X-Request-Id (400 do handler e 401 do resource server)")
    void problemDetailCarriesRequestId() throws Exception {
        var badRequest = mockMvc.perform(post("/api/v1/auth/reset-password").header("X-Request-Id", "rid-400")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ResetPasswordRequest("nao-existe", NEW_PASSWORD)))).andReturn();
        var unauthorized = mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer lixo")).andReturn();

        assertThat(badRequest.getResponse().getStatus()).isEqualTo(400);
        assertThat(json(badRequest).get("requestId").asText()).isEqualTo("rid-400")
                .isEqualTo(badRequest.getResponse().getHeader("X-Request-Id"));
        assertThat(unauthorized.getResponse().getStatus()).isEqualTo(401);
        assertThat(json(unauthorized).get("requestId").asText())
                .isEqualTo(unauthorized.getResponse().getHeader("X-Request-Id"));
    }
}
