package com.autofolio.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.autofolio.auth.repository.PasswordResetTokenRepository;
import com.autofolio.auth.repository.RefreshTokenRepository;
import com.autofolio.auth.repository.UserProfileRepository;
import com.autofolio.auth.repository.UserRepository;
import com.autofolio.vehicle.repository.VehicleRepository;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Teste que faz upload real no Cloudinary (rede e credenciais reais).
 *
 * <p>Tag {@code external}: fora do {@code mvn test} padrão (ADR-001 §6.3). Rode sob demanda com
 * {@code mvn test -Pexternal-tests}. Sem as credenciais {@code CLOUDINARY_*} ele é ignorado.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
})
@ActiveProfiles("dev")
@Tag("external")
@MockBean({UserRepository.class, UserProfileRepository.class, RefreshTokenRepository.class,
        PasswordResetTokenRepository.class, VehicleRepository.class, TransactionTemplate.class})
public class CloudinaryIntegrationTest {

    private static final List<String> CREDENTIALS =
            List.of("CLOUDINARY_CLOUD_NAME", "CLOUDINARY_API_KEY", "CLOUDINARY_API_SECRET");

    @Autowired
    private Cloudinary cloudinary;

    /**
     * Ignora o teste (sem subir o contexto) se faltar alguma credencial, no ambiente ou no .env.
     */
    @BeforeAll
    static void requireCredentials() throws ClassNotFoundException {
        // Carrega o .env como System properties (bloco estático do AutoFolioApplication)
        Class.forName("com.autofolio.AutoFolioApplication");
        for (String name : CREDENTIALS) {
            String value = System.getenv(name) != null
                    ? System.getenv(name) : System.getProperty(name);
            Assumptions.assumeTrue(value != null && !value.isBlank(),
                    "Credencial " + name + " ausente: teste externo ignorado");
        }
    }

    // Thread separada: leitura de socket bloqueada não responde a interrupt
    @Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
    @Test
    @DisplayName("Deve realizar upload de uma imagem real para validar a integração com Cloudinary")
    void shouldUploadImageSuccessfullyWhenCloudinaryIsConfigured() throws IOException {
        String fordLogoUrl = "https://upload.wikimedia.org/wikipedia/commons/a/a0/Ford_Motor_Company_Logo.svg";

        Map<?, ?> options = ObjectUtils.asMap(
                "folder", "autofolio_tests",
                "public_id", "ford_logo_test_" + System.currentTimeMillis(),
                "connect_timeout", 5_000,
                "timeout", 30_000
        );

        Map<?, ?> uploadResult = cloudinary.uploader().upload(fordLogoUrl, options);

        assertThat(uploadResult).isNotNull();
        assertThat(uploadResult.get("public_id")).isNotNull();
        assertThat(uploadResult.get("secure_url")).isNotNull();

        String publicUrl = (String) uploadResult.get("secure_url");
        System.out.println("✅ Upload bem-sucedido!");
        System.out.println("🔗 URL Pública: " + publicUrl);
    }
}
