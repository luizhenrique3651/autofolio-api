package com.autofolio.auth;

import com.autofolio.config.FrontendProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Monta e envia o e-mail HTML de redefinição de senha.
 * O template usa atributos HTML antigos (width, cellpadding, cellspacing, align) de propósito:
 * clientes de e-mail como o Outlook ignoram o CSS equivalente.
 */
@Component
public class PasswordResetMailer {

    // Só fallback para MAIL_FROM em branco (o default de propriedade ausente é o do application.yml)
    static final String DEFAULT_FROM = "noreply@autofolio.com";
    static final String SUBJECT = "AutoFolio - Redefinição de senha";
    private static final String TEMPLATE = "templates/email/password-reset.html";

    private final JavaMailSender mailSender;
    private final String frontendUrl;
    private final String from;
    private final String template;

    public PasswordResetMailer(JavaMailSender mailSender,
                               FrontendProperties frontend,
                               @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.frontendUrl = frontend.url();
        this.from = from.isBlank() ? DEFAULT_FROM : from;
        try (var in = new ClassPathResource(TEMPLATE).getInputStream()) {
            this.template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Template de e-mail não encontrado: " + TEMPLATE, e);
        }
    }

    /**
     * Envia o e-mail (bloqueante: chamar fora de transação e fora da thread da requisição).
     */
    public void send(String to, String name, String token, Duration validity) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            var helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(SUBJECT);
            helper.setText(render(name, token, validity), true);
        } catch (MessagingException e) {
            throw new MailPreparationException(e);
        }
        mailSender.send(message);
    }

    String render(String name, String token, Duration validity) {
        String link = frontendUrl + "/reset-password?token=" + token;
        return template
                .replace("{{name}}", HtmlUtils.htmlEscape(name))
                .replace("{{validity}}", describe(validity))
                .replace("{{link}}", HtmlUtils.htmlEscape(link));
    }

    private static String describe(Duration validity) {
        long hours = validity.toHours();
        if (hours >= 1 && validity.equals(Duration.ofHours(hours))) {
            return hours == 1 ? "1 hora" : hours + " horas";
        }
        long minutes = validity.toMinutes();
        return minutes == 1 ? "1 minuto" : minutes + " minutos";
    }
}
