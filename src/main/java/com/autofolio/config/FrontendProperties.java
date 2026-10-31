package com.autofolio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * URL base do frontend (frontend.url), usada no CORS e no link do e-mail de redefinição de senha.
 * A barra final é removida aqui, num só lugar.
 */
@ConfigurationProperties(prefix = "frontend")
public record FrontendProperties(String url) {

    public FrontendProperties {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("frontend.url é obrigatório");
        }
        url = url.replaceAll("/+$", "");
    }
}
