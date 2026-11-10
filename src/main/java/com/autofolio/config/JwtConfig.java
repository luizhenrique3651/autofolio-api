package com.autofolio.config;

import com.autofolio.auth.security.ActiveAccountValidator;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Beans de codificação/validação de JWT (HS256) com chave simétrica vinda de JWT_SECRET.
 */
@Configuration
public class JwtConfig {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey secretKey;

    /**
     * Valida o tamanho mínimo do segredo JWT e cria a chave HS256.
     */
    public JwtConfig(@Value("${jwt.secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET deve ter no mínimo "
                    + MIN_SECRET_BYTES + " bytes.");
        }
        this.secretKey = new SecretKeySpec(bytes, "HmacSHA256");
    }

    /**
     * Codificador usado para assinar os access tokens (HS256).
     */
    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
    }

    /**
     * Decodificador usado para validar os access tokens: assinatura, expiração e conta ativa.
     */
    @Bean
    public JwtDecoder jwtDecoder(ActiveAccountValidator activeAccountValidator) {
        var decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256).build();
        // Além de assinatura e expiração, o token só vale para conta ativa (ADR-001, Action
        // Items nº 15)
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), activeAccountValidator));
        return decoder;
    }
}
