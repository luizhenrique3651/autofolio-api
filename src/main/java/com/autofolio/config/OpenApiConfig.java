package com.autofolio.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Declara no Swagger o esquema Bearer JWT usado pelos endpoints autenticados.
 */
@Configuration
@SecurityScheme(name = OpenApiConfig.BEARER, type = SecuritySchemeType.HTTP, scheme = "bearer",
        bearerFormat = "JWT",
        description = "Access token JWT. Todo endpoint autenticado confere se a conta está ativa "
                + "(401 se desativada) e responde 503 se o banco estiver indisponível nessa "
                + "checagem.")
public class OpenApiConfig {

    /** Nome do esquema, referenciado por {@code @SecurityRequirement}. */
    public static final String BEARER = "bearerAuth";
}
