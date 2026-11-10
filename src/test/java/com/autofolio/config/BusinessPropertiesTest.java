package com.autofolio.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("Testes Unitários - BusinessProperties")
class BusinessPropertiesTest {

    @Test
    @DisplayName("Fuso nulo falha com mensagem que nomeia a propriedade")
    void shouldRequireZone() {
        assertThatThrownBy(() -> new BusinessProperties(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("business.zone é obrigatório");
    }

    @Test
    @DisplayName("business.zone é ligado de forma tipada a ZoneId")
    void shouldBindZone() {
        new ApplicationContextRunner()
                .withUserConfiguration(Config.class)
                .withPropertyValues("business.zone=Europe/Lisbon")
                .run(context -> assertThat(context.getBean(BusinessProperties.class).zone())
                        .isEqualTo(ZoneId.of("Europe/Lisbon")));
    }

    @EnableConfigurationProperties(BusinessProperties.class)
    static class Config {
    }
}
