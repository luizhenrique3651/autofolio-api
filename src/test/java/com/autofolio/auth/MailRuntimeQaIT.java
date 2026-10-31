package com.autofolio.auth;

import com.autofolio.AbstractPostgresIT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** QA AF-011 (rodada 2): sem profile, JavaMailSender real, SMTP falso local, MAIL_FROM vazio. */
@SpringBootTest(properties = {
        "jwt.secret=chave-de-teste-com-pelo-menos-32-caracteres!!",
        "cloudinary.cloud-name=teste", "cloudinary.api-key=teste", "cloudinary.api-secret=teste",
        "app.mail.from="
})
@AutoConfigureMockMvc
@Tag("integration")
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class MailRuntimeQaIT extends AbstractPostgresIT {

    static final List<String> SMTP = new CopyOnWriteArrayList<>();
    static ServerSocket server;

    @DynamicPropertySource
    static void smtp(DynamicPropertyRegistry r) throws Exception {
        server = new ServerSocket(0);
        Thread.ofVirtual().start(() -> {
            try {
                while (!server.isClosed()) {
                    var s = server.accept();
                    var in = new BufferedReader(new InputStreamReader(s.getInputStream()));
                    var out = new PrintWriter(s.getOutputStream(), true, java.nio.charset.StandardCharsets.UTF_8);
                    out.print("220 fake\r\n"); out.flush();
                    String l; boolean data = false;
                    while ((l = in.readLine()) != null) {
                        if (data) { SMTP.add("D:" + l); if (l.equals(".")) { data = false; out.print("250 ok\r\n"); out.flush(); } continue; }
                        SMTP.add(l);
                        String u = l.toUpperCase();
                        if (u.startsWith("EHLO")) out.print("250 fake\r\n");
                        else if (u.startsWith("DATA")) { data = true; out.print("354 go\r\n"); }
                        else if (u.startsWith("QUIT")) { out.print("221 bye\r\n"); out.flush(); break; }
                        else out.print("250 ok\r\n");
                        out.flush();
                    }
                    s.close();
                }
            } catch (Exception ignored) { }
        });
        r.add("spring.mail.host", () -> "127.0.0.1");
        r.add("spring.mail.port", () -> server.getLocalPort());
    }

    @AfterAll
    static void stop() throws Exception { server.close(); }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    @Order(1)
    @DisplayName("MAIL_FROM vazio → MAIL FROM noreply@autofolio.com (SMTP real, sem profile)")
    void emptyFromFallsBack() throws Exception {
        jdbc.update("DELETE FROM password_reset_tokens"); jdbc.update("DELETE FROM user_profiles"); jdbc.update("DELETE FROM users");
        jdbc.update("INSERT INTO users(name,email,password_hash,slug) VALUES ('U','a@t.com','x','s1')");
        var r = mockMvc.perform(post("/api/v1/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"a@t.com\"}")).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(202);
        await().untilAsserted(() -> assertThat(SMTP).anyMatch(s -> s.startsWith("D:") && s.equals("D:.")));
        System.out.println("QA-SMTP " + SMTP.stream().filter(s -> !s.startsWith("D:") || s.startsWith("D:From:") || s.startsWith("D:To:") || s.startsWith("D:Subject:")).toList());
        assertThat(SMTP).anyMatch(s -> s.toUpperCase().startsWith("MAIL FROM:<NOREPLY@AUTOFOLIO.COM>"));
        assertThat(SMTP).anyMatch(s -> s.startsWith("D:From: noreply@autofolio.com"));
    }

    @Autowired org.springframework.boot.actuate.health.HealthEndpoint health;

    @Test
    @Order(2)
    @DisplayName("health UP rápido com SMTP inacessível")
    void healthUp() throws Exception {
        server.close();
        long t = System.nanoTime();
        var h = (org.springframework.boot.actuate.health.CompositeHealth) health.health();
        long ms = (System.nanoTime() - t) / 1_000_000;
        System.out.println("QA-HEALTH " + h.getStatus() + " " + h.getComponents().keySet() + " " + ms + "ms");
        assertThat(h.getStatus().getCode()).isEqualTo("UP");
        assertThat(h.getComponents()).doesNotContainKey("mail");
        assertThat(ms).isLessThan(1000);
    }
}
