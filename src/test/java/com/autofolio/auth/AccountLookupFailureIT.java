package com.autofolio.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.autofolio.AbstractPostgresIT;
import com.autofolio.auth.repository.UserRepository;
import com.autofolio.config.BusinessProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Tag("integration")
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Testes de Integração - falha de banco na autenticação e fuso do negócio")
class AccountLookupFailureIT extends AbstractPostgresIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private BusinessProperties business;

    @SpyBean
    private UserRepository userRepository;
    @MockBean
    private PasswordResetMailer mailer;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        reset(userRepository);
        jdbc.update("DELETE FROM vehicles");
        jdbc.update("DELETE FROM password_reset_tokens");
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM user_profiles");
        jdbc.update("DELETE FROM users");
    }

    @Test
    @DisplayName("Banco fora na checagem da conta: 503 ProblemDetail com requestId, ERROR com "
            + "stacktrace uma vez, e nenhuma página de erro do container")
    void shouldAnswer503WhenDatabaseFailsDuringAuthentication(CapturedOutput output)
            throws Exception {
        var register = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Vendedor",
                        "email", "a@teste.com", "slug", "vendedor-a",
                        "password", "SenhaForte1")))).andReturn();
        var token = objectMapper.readTree(register.getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).get("accessToken").asText();
        doThrow(new DataAccessResourceFailureException("conexão recusada"))
                .when(userRepository).existsByIdAndActiveTrue(any());

        var result = mockMvc.perform(get("/api/v1/users/me")
                .header("Authorization", "Bearer " + token)
                .header("X-Request-Id", "req-falha-db")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
        var body = objectMapper.readTree(result.getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
        assertThat(body.get("status").asInt()).isEqualTo(503);
        assertThat(body.get("title").asText()).isEqualTo("Serviço Indisponível");
        assertThat(body.get("requestId").asText()).isEqualTo("req-falha-db");
        assertThat(body.get("path").asText()).isEqualTo("/api/v1/users/me");
        assertThat(body.toString()).doesNotContain("conexão recusada");
        assertThat(output.getOut().split("Falha ao consultar a conta", -1)).hasSize(2);
        assertThat(output.getOut()).contains("ERROR").contains("[req-falha-db]")
                .contains("DataAccessResourceFailureException");
    }

    @Test
    @DisplayName("business.zone tem default America/Sao_Paulo no application.yml")
    void shouldDefaultBusinessZoneToSaoPaulo() {
        assertThat(business.zone()).isEqualTo(ZoneId.of("America/Sao_Paulo"));
    }
}
