package com.autofolio.config;

import com.autofolio.auth.repository.UserProfileRepository;
import com.autofolio.auth.repository.UserRepository;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private UserProfileRepository userProfileRepository;

    @Test
    @DisplayName("Deve realizar upload de uma imagem real para validar a integração com Cloudinary")
    void shouldUploadImageSuccessfullyWhenCloudinaryIsConfigured() throws IOException {
        String fordLogoUrl = "https://upload.wikimedia.org/wikipedia/commons/a/a0/Ford_Motor_Company_Logo.svg";

        Map<String, Object> options = ObjectUtils.asMap(
            "folder", "autofolio_tests",
            "public_id", "ford_logo_test_" + System.currentTimeMillis()
        );

        Map uploadResult = cloudinary.uploader().upload(fordLogoUrl, options);

        assertThat(uploadResult).isNotNull();
        assertThat(uploadResult.get("public_id")).isNotNull();
        assertThat(uploadResult.get("secure_url")).isNotNull();

        String publicUrl = (String) uploadResult.get("secure_url");
        System.out.println("✅ Upload bem-sucedido!");
        System.out.println("🔗 URL Pública: " + publicUrl);
    }
}
