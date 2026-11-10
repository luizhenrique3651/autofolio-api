package com.autofolio.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Testes Unitários - CloudinaryProperties")
class CloudinaryPropertiesTest {

    @Test
    @DisplayName("Guarda os dois timeouts")
    void shouldKeepTimeouts() {
        var props = new CloudinaryProperties(Duration.ofSeconds(5), Duration.ofSeconds(15));

        assertThat(props.connectTimeout()).hasSeconds(5);
        assertThat(props.readTimeout()).hasSeconds(15);
    }

    @Test
    @DisplayName("Timeout ausente falha na subida (cliente de rede sem timeout é proibido)")
    void shouldRejectMissingTimeouts() {
        assertThatThrownBy(() -> new CloudinaryProperties(null, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CloudinaryProperties(Duration.ofSeconds(1), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
