package com.autofolio.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registra o FrontendProperties, usado pelo CORS (SecurityConfig) e pelo e-mail de redefinição de senha.
 */
@Configuration
@EnableConfigurationProperties(FrontendProperties.class)
public class FrontendConfig {
}
