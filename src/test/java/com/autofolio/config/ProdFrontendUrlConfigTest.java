package com.autofolio.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Resolve o frontend.url com os ymls reais (prod sobre o base), sem subir o contexto: o prod não pode herdar
 * o localhost do base, senão subiria sem FRONTEND_URL com CORS e link de e-mail apontando para o lugar errado.
 */
@DisplayName("Testes Unitários - frontend.url no perfil prod")
class ProdFrontendUrlConfigTest {

    private StandardEnvironment environment(String profileFile, Map<String, Object> variables) throws IOException {
        var env = new StandardEnvironment();
        var loader = new YamlPropertySourceLoader();
        env.getPropertySources().addFirst(new MapPropertySource("variaveis", variables));
        // Ordem de precedência real: variáveis > yml do perfil > application.yml
        var sources = env.getPropertySources();
        sources.addLast(loader.load("base", new ClassPathResource("application.yml")).getFirst());
        if (profileFile != null) {
            sources.addBefore("base", loader.load("perfil", new ClassPathResource(profileFile)).getFirst());
        }
        return env;
    }

    @Test
    @DisplayName("prod sem FRONTEND_URL não resolve a propriedade (a aplicação não sobe)")
    void prodFailsWithoutFrontendUrl() throws IOException {
        var env = environment("application-prod.yml", Map.of());

        assertThatThrownBy(() -> env.getProperty("frontend.url"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FRONTEND_URL");
    }

    @Test
    @DisplayName("prod com FRONTEND_URL usa o valor definido")
    void prodUsesFrontendUrl() throws IOException {
        var env = environment("application-prod.yml", Map.of("FRONTEND_URL", "https://app.autofolio.com"));

        assertThat(env.getProperty("frontend.url")).isEqualTo("https://app.autofolio.com");
    }

    @Test
    @DisplayName("base (dev/test) mantém o default localhost")
    void baseKeepsLocalhostDefault() throws IOException {
        var env = environment(null, Map.of());

        assertThat(env.getProperty("frontend.url")).isEqualTo("http://localhost:5173");
    }
}
