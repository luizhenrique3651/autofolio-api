package com.autofolio.auth;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Testes Unitários - PasswordResetMailer")
class PasswordResetMailerTest {

    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final PasswordResetMailer mailer =
            new PasswordResetMailer(sender, "https://app.autofolio.com/", "noreply@autofolio.com");

    @Test
    @DisplayName("Deve renderizar nome, prazo e link visível no HTML")
    void shouldRenderTemplate() {
        var html = mailer.render("Luiz", "tok-123", Duration.ofHours(1));

        assertThat(html)
                .contains("AutoFolio")
                .contains("Olá, Luiz!")
                .contains("1 hora")
                .contains("href=\"https://app.autofolio.com/reset-password?token=tok-123\"")
                .contains(">https://app.autofolio.com/reset-password?token=tok-123</a>")
                .doesNotContain("{{");
    }

    @Test
    @DisplayName("Deve escapar HTML no nome do usuário")
    void shouldEscapeName() {
        var html = mailer.render("<script>x</script>", "t", Duration.ofHours(1));

        assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;");
    }

    @Test
    @DisplayName("Deve descrever prazos em minutos e múltiplas horas")
    void shouldDescribeValidity() {
        assertThat(mailer.render("a", "t", Duration.ofMinutes(30))).contains("30 minutos");
        assertThat(mailer.render("a", "t", Duration.ofMinutes(1))).contains("1 minuto");
        assertThat(mailer.render("a", "t", Duration.ofMinutes(90))).contains("90 minutos");
        assertThat(mailer.render("a", "t", Duration.ofHours(2))).contains("2 horas");
    }

    @Test
    @DisplayName("Deve enviar mensagem MIME com destinatário, assunto e remetente")
    void shouldSend() throws Exception {
        MimeMessage message = new JavaMailSenderImpl().createMimeMessage();
        when(sender.createMimeMessage()).thenReturn(message);

        mailer.send("dest@teste.com", "Luiz", "tok", Duration.ofHours(1));

        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        assertThat(captor.getValue().getAllRecipients()[0].toString()).isEqualTo("dest@teste.com");
        assertThat(captor.getValue().getSubject()).isEqualTo(PasswordResetMailer.SUBJECT);
        assertThat(captor.getValue().getFrom()[0].toString()).isEqualTo("noreply@autofolio.com");
    }

    @Test
    @DisplayName("Deve usar remetente padrão quando o configurado está em branco")
    void shouldFallbackFrom() throws Exception {
        var blank = new PasswordResetMailer(sender, "http://x", " ");
        when(sender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());

        blank.send("d@t.com", "L", "t", Duration.ofHours(1));

        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        assertThat(captor.getValue().getFrom()[0].toString()).isEqualTo(PasswordResetMailer.DEFAULT_FROM);
    }
}
