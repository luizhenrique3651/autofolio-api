package com.autofolio.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Timeouts do cliente Cloudinary (ADR-001 §4.2). As credenciais continuam em
 * {@link CloudinaryConfig}.
 *
 * @param connectTimeout tempo máximo para conectar
 * @param readTimeout tempo máximo de espera pela resposta do upload
 */
@ConfigurationProperties(prefix = "cloudinary")
public record CloudinaryProperties(Duration connectTimeout, Duration readTimeout) {

    /**
     * Exige os dois timeouts: cliente de rede sem timeout é proibido.
     */
    public CloudinaryProperties {
        if (connectTimeout == null || readTimeout == null) {
            throw new IllegalArgumentException(
                    "cloudinary.connect-timeout e cloudinary.read-timeout são obrigatórios");
        }
    }
}
