package com.autofolio.config;

import com.autofolio.AbstractPostgresIT;
import com.autofolio.shared.web.RequestIdFilter;
import jakarta.mail.internet.MimeMessage;
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
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FRONTEND_URL (a variável que o .env fornece) deve chegar ao CORS e ao link do e-mail pelo mapeamento
 * frontend.url do application.yml. A variável é injetada como propriedade do ambiente, como faz o dotenv.
 */
@SpringBootTest(properties = "FRONTEND_URL=https://app.exemplo.com/")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Tag("integration")
@DisplayName("Testes de Integração - FRONTEND_URL no CORS e no e-mail")
class FrontendUrlIT extends AbstractPostgresIT {

    private static final String ORIGIN = "https://app.exemplo.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @MockBean
    private JavaMailSender sender;

    @BeforeEach
    void setUp() {
        when(sender.createMimeMessage()).thenAnswer(i -> new JavaMailSenderImpl().createMimeMessage());
        jdbc.update("DELETE FROM password_reset_tokens");
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM user_profiles");
        jdbc.update("DELETE FROM users");
    }

    @Test
    @DisplayName("Preflight do FRONTEND_URL com X-Request-Id é permitido")
    void preflightAllowsRequestIdHeader() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", RequestIdFilter.HEADER))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString(RequestIdFilter.HEADER)));
    }

    @Test
    @DisplayName("Resposta expõe X-Request-Id ao frontend")
    void responseExposesRequestId() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("Origin", ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(header().string("Access-Control-Expose-Headers", containsString(RequestIdFilter.HEADER)))
                .andExpect(header().exists(RequestIdFilter.HEADER));
    }

    @Test
    @DisplayName("O localhost padrão não é aceito quando FRONTEND_URL aponta para outra origem")
    void defaultLocalhostIsNotAccepted() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("Origin", "http://localhost:5173")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Link do e-mail usa o FRONTEND_URL (sem barra final)")
    void emailLinkUsesFrontendUrl() throws Exception {
        jdbc.update("INSERT INTO users(name,email,password_hash,slug) VALUES ('U','a@t.com','x','s-front')");
        mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@t.com\"}")).andExpect(status().isAccepted());

        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender, timeout(5000)).send(captor.capture());
        assertThat((String) captor.getValue().getContent()).contains("https://app.exemplo.com/reset-password?token=");
    }
}
