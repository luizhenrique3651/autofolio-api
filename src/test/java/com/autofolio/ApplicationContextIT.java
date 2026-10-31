package com.autofolio;

import com.autofolio.auth.PasswordResetMailer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sobe o contexto completo SEM profile e SEM mocks, para pegar beans que dependem de configuração
 * ausente no application.yml base (ex.: JavaMailSender só existe com spring.mail.host).
 */
@SpringBootTest(properties = {
        "jwt.secret=chave-de-teste-com-pelo-menos-32-caracteres!!",
        "cloudinary.cloud-name=teste",
        "cloudinary.api-key=teste",
        "cloudinary.api-secret=teste"
})
@Tag("integration")
@DisplayName("Teste de Integração - contexto completo sem profile")
class ApplicationContextIT extends AbstractPostgresIT {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private PasswordResetMailer passwordResetMailer;

    @Test
    @DisplayName("Deve subir o contexto com JavaMailSender e PasswordResetMailer reais")
    void contextLoads() {
        assertThat(mailSender).isNotNull();
        assertThat(passwordResetMailer).isNotNull();
    }
}
