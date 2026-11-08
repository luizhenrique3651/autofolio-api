package com.autofolio;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base dos testes de integração: um único Postgres (Testcontainers) compartilhado por todas as
 * classes da JVM, iniciado uma vez e encerrado pelo Ryuk ao fim da execução.
 */
public abstract class AbstractPostgresIT {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Segredos de teste fixos: os ITs não dependem do .env (dotenv grava System properties
        // para a JVM inteira) nem de variáveis do SO, então passam em qualquer ordem e no CI.
        registry.add("jwt.secret", () -> "chave-de-teste-com-pelo-menos-32-caracteres!!");
        registry.add("cloudinary.cloud-name", () -> "teste");
        registry.add("cloudinary.api-key", () -> "teste");
        registry.add("cloudinary.api-secret", () -> "teste");
    }
}
