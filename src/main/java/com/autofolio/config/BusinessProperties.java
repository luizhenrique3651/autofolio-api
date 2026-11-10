package com.autofolio.config;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Fuso horário do negócio ({@code business.zone}), usado nas regras que dependem da data civil
 * local (ex.: o ano atual no teto de {@code yearFab}). O {@code Clock} global segue em UTC.
 */
@ConfigurationProperties(prefix = "business")
public record BusinessProperties(ZoneId zone) {

    /**
     * Exige que {@code business.zone} esteja preenchida (o default fica no application.yml).
     */
    public BusinessProperties {
        if (zone == null) {
            throw new IllegalArgumentException("business.zone é obrigatório");
        }
    }
}
