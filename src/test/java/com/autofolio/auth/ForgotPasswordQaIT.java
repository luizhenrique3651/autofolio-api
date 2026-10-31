package com.autofolio.auth;

import com.autofolio.shared.security.TokenHasher;
import java.util.regex.Pattern;
import com.autofolio.AbstractPostgresIT;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** QA AF-011: fluxo ponta a ponta com o PasswordResetMailer real e JavaMailSender mockado. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Tag("integration")
@ExtendWith(OutputCaptureExtension.class)
class ForgotPasswordQaIT extends AbstractPostgresIT {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired Environment env;
    @MockBean JavaMailSender sender;

    @BeforeEach
    void setUp() {
        reset(sender);
        when(sender.createMimeMessage()).thenAnswer(i -> new JavaMailSenderImpl().createMimeMessage());
        jdbc.update("DELETE FROM password_reset_tokens");
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM user_profiles");
        jdbc.update("DELETE FROM users");
    }

    private void insertUser(String name, String email, boolean profile) {
        jdbc.update("INSERT INTO users(name,email,password_hash,slug) VALUES (?,?,'x',?)", name, email, "s-" + System.nanoTime());
        if (profile) {
            jdbc.update("INSERT INTO user_profiles(user_id) SELECT id FROM users WHERE email=?", email);
        }
    }

    private MvcResult call(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
    }

    private String body(MvcResult r) throws Exception {
        return r.getResponse().getContentAsString();
    }

    @Test
    @DisplayName("202 idêntico (status, headers, corpo) p/ existente, sem perfil e inexistente")
    void identicalResponses() throws Exception {
        insertUser("Com Perfil", "a@t.com", true);
        insertUser("Sem Perfil", "b@t.com", false);
        var a = call("{\"email\":\"a@t.com\"}");
        var b = call("{\"email\":\"b@t.com\"}");
        var c = call("{\"email\":\"zzz@t.com\"}");
        for (var r : new MvcResult[]{b, c}) {
            assertThat(r.getResponse().getStatus()).isEqualTo(202);
            assertThat(body(r)).isEqualTo(body(a));
            assertThat(r.getResponse().getHeaderNames()).isEqualTo(a.getResponse().getHeaderNames());
            for (var h : a.getResponse().getHeaderNames()) {
                // X-Request-Id é único por requisição, de propósito
                if (h.equals("X-Request-Id")) {
                    continue;
                }
                assertThat(r.getResponse().getHeaders(h)).as(h).isEqualTo(a.getResponse().getHeaders(h));
            }
        }
        assertThat(a.getResponse().getStatus()).isEqualTo(202);
        await().untilAsserted(() -> assertThat(jdbc.queryForObject("SELECT count(*) FROM password_reset_tokens", Integer.class)).isEqualTo(2));
    }

    @Test
    @DisplayName("Resposta não espera o envio (SMTP lento 3s)")
    void doesNotWaitForSmtp() throws Exception {
        insertUser("U", "a@t.com", true);
        doAnswer(i -> { Thread.sleep(3000); return null; }).when(sender).send(any(MimeMessage.class));
        call("{\"email\":\"warm@t.com\"}");
        long t = System.nanoTime();
        var r = call("{\"email\":\"a@t.com\"}");
        long ms = (System.nanoTime() - t) / 1_000_000;
        assertThat(r.getResponse().getStatus()).isEqualTo(202);
        assertThat(ms).as("ms").isLessThan(1000);
        verify(sender, timeout(5000)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("E-mail enviado: link com token do banco, HTML escapado, prazo, From")
    void renderedMail() throws Exception {
        insertUser("<script>alert(1)</script>", "a@t.com", true);
        var sent = new AtomicReference<MimeMessage>();
        doAnswer(i -> { sent.set(i.getArgument(0)); return null; }).when(sender).send(any(MimeMessage.class));
        call("{\"email\":\"a@t.com\"}");
        await().untilAsserted(() -> assertThat(sent.get()).isNotNull());
        String tokenHash = jdbc.queryForObject("SELECT token_hash FROM password_reset_tokens", String.class);
        var m = sent.get();
        String html = (String) m.getContent();
        String frontend = env.getRequiredProperty("frontend.url");
        var linkMatcher = Pattern.compile(Pattern.quote(frontend + "/reset-password?token=") + "([0-9a-f-]{36})").matcher(html);
        assertThat(linkMatcher.find()).isTrue();
        // O e-mail leva o token cru; o banco guarda só o hash dele
        String token = linkMatcher.group(1);
        assertThat(tokenHash).isEqualTo(TokenHasher.sha256Hex(token)).isNotEqualTo(token);
        assertThat(html).contains(frontend + "/reset-password?token=" + token);
        assertThat(html).contains("&lt;script&gt;").doesNotContain("<script>");
        assertThat(html).contains("AutoFolio").contains("1 hora");
        assertThat(m.getSubject()).contains("AutoFolio");
        assertThat(m.getFrom()[0].toString()).isEqualTo("noreply@autofolio.com");
        assertThat(m.getAllRecipients()[0].toString()).isEqualTo("a@t.com");
        System.out.println("QA-LINK " + frontend + "/reset-password?token=" + token);
    }

    @Test
    @DisplayName("Falha SMTP: 202, ERROR com userId e causa raiz, sem e-mail")
    void smtpFailureLogs(CapturedOutput out) throws Exception {
        insertUser("U", "pii-addr@t.com", true);
        doThrow(new MailSendException("Mail server connection failed", new java.net.SocketTimeoutException("Read timed out")))
                .when(sender).send(any(MimeMessage.class));
        assertThat(call("{\"email\":\"pii-addr@t.com\"}").getResponse().getStatus()).isEqualTo(202);
        await().untilAsserted(() -> assertThat(out.getAll()).contains("Falha ao enviar e-mail de redefinição"));
        String userId = jdbc.queryForObject("SELECT id::text FROM users", String.class);
        var line = out.getAll().lines().filter(l -> l.contains("Falha ao enviar e-mail")).findFirst().orElseThrow();
        assertThat(line).contains("ERROR").contains(userId).contains("SocketTimeoutException");
        assertThat(out.getAll()).doesNotContain("pii-addr@t.com");
    }

    @Test
    @DisplayName("400: ausente, nulo, branco, >255, malformado, sem corpo, tipo errado")
    void badRequests() throws Exception {
        String longEmail = "a".repeat(250) + "@t.com";
        for (String json : new String[]{"{}", "{\"email\":null}", "{\"email\":\"  \"}", "{\"email\":\"x\"}",
                "{\"email\":\"" + longEmail + "\"}", "{malformado", "", "{\"email\":123}", "[]"}) {
            var r = call(json);
            System.out.println("QA-400 [" + (json.length() > 40 ? json.substring(0, 20) + "…" : json) + "] -> " + r.getResponse().getStatus()
                    + " " + r.getResponse().getContentType() + " " + body(r));
            assertThat(r.getResponse().getStatus()).as(json).isEqualTo(400);
            assertThat(r.getResponse().getContentType()).contains("application/problem+json");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM password_reset_tokens", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Timeouts SMTP 5000 e props MAIL_* configuradas")
    void mailConfig() {
        for (var p : new String[]{"connectiontimeout", "timeout", "writetimeout"}) {
            assertThat(env.getProperty("spring.mail.properties.mail.smtp." + p)).isEqualTo("5000");
        }
        assertThat(env.getProperty("spring.mail.host")).isNotBlank();
        assertThat(env.getProperty("app.mail.from")).isNotBlank();
    }
}
