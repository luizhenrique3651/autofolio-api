package com.autofolio.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuração central de segurança utilizando Spring Security 6.
 * Define políticas de acesso, CORS e estado da sessão (stateless).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${frontend.url:http://localhost:5173}")
    private String frontendUrl;

    /**
     * Define a corrente de filtros de segurança (Security Filter Chain).
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable) // Desabilita CSRF para APIs stateless
                .cors(cors -> cors.configurationSource(corsConfigurationSource())) // Configura CORS
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // API sem estado
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll() // Rotas de login/registro públicas
                        .requestMatchers("/api/v1/portfolio/**").permitAll() // Portfólio público
                        .requestMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll() // Documentação Swagger
                        .anyRequest().authenticated() // Qualquer outra rota exige autenticação
                )
                .formLogin(AbstractHttpConfigurer::disable) // Remove formulário padrão
                .httpBasic(AbstractHttpConfigurer::disable) // Remove autenticação básica
                .build();
    }

    /**
     * Configuração de CORS (Cross-Origin Resource Sharing).
     * Permite que o frontend acesse a API de forma segura.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        var configuration = new CorsConfiguration();
        
        // Define origens permitidas baseadas na variável de ambiente
        configuration.setAllowedOrigins(List.of(frontendUrl));
        
        // Métodos HTTP permitidos
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        
        // Headers permitidos
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin"));
        
        // Permite o envio de cookies/credentials se necessário (útil para alguns fluxos JWT)
        configuration.setAllowCredentials(true);
        
        // Tempo que o navegador pode manter a resposta do pre-flight em cache
        configuration.setMaxAge(3600L);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
