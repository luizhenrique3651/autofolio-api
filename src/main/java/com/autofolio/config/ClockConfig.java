package com.autofolio.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Relógio injetável, para que testes de expiração sejam determinísticos.
 */
@Configuration
@EnableConfigurationProperties(BusinessProperties.class)
public class ClockConfig {

    /**
     * Relógio em UTC, injetável para permitir testes determinísticos.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
