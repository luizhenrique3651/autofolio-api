package com.autofolio.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
    "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
    "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
})
@ActiveProfiles("dev")
@Tag("integration")
public class CloudinaryIntegrationTest {

    @Autowired
    private Cloudinary cloudinary;

    @Test
    @DisplayName("Deve realizar upload de uma imagem real para validar a integração com Cloudinary")
    void shouldUploadImageSuccessfullyWhenCloudinaryIsConfigured() throws IOException {
        // Arrange
        String fordLogoUrl = "https://upload.wikimedia.org/wikipedia/commons/a/a0/Ford_Motor_Company_Logo.svg";

        Map<String, Object> options = ObjectUtils.asMap(
            "folder", "autofolio_tests",
            "public_id", "ford_logo_test_" + System.currentTimeMillis()
        );

        // Act
        Map uploadResult = cloudinary.uploader().upload(fordLogoUrl, options);

        // Assert
        assertThat(uploadResult).isNotNull();
        assertThat(uploadResult.get("public_id")).isNotNull();
        assertThat(uploadResult.get("secure_url")).isNotNull();

        String publicUrl = (String) uploadResult.get("secure_url");
        System.out.println("✅ Upload bem-sucedido!");
        System.out.println("🔗 URL Pública: " + publicUrl);
    }
}
