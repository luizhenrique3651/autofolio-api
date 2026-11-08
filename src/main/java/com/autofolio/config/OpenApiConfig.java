package com.autofolio.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Declara no Swagger o esquema Bearer JWT usado pelos endpoints autenticados.
 */
@Configuration
@SecurityScheme(name = OpenApiConfig.BEARER, type = SecuritySchemeType.HTTP, scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {

    /** Nome do esquema, referenciado por {@code @SecurityRequirement}. */
    public static final String BEARER = "bearerAuth";
}
