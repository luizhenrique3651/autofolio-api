package com.autofolio.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.autofolio.AbstractPostgresIT;
import com.autofolio.auth.PasswordResetMailer;
import com.autofolio.shared.validation.TestImages;
import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;

/**
 * POST /profile/photo com Tomcat real (limite multipart de verdade) e Postgres via Testcontainers.
 * O Cloudinary é mockado: nenhuma chamada de rede.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@Tag("integration")
@DisplayName("Testes de Integração - POST /profile/photo")
class ProfilePhotoIT extends AbstractPostgresIT {

    private static final String URL = "https://res.cloudinary.com/x/image/upload/profiles/a.png";
    private static final byte[] PNG = TestImages.png();

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private PasswordResetMailer mailer;
    @MockBean
    private Cloudinary cloudinary;
    @MockBean
    private Uploader uploader;

    private String token;

    @BeforeEach
    void setUp() {
        // O cliente padrão (HttpURLConnection) não suporta 401 com corpo em streaming
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        reset(mailer, cloudinary, uploader);
        when(cloudinary.uploader()).thenReturn(uploader);
        jdbc.update("DELETE FROM password_reset_tokens");
        jdbc.update("DELETE FROM refresh_tokens");
        jdbc.update("DELETE FROM user_profiles");
        jdbc.update("DELETE FROM users");
        var body = rest.postForObject("/api/v1/auth/register", Map.of("name", "Vendedor",
                "email", "vendedor@teste.com", "slug", "vendedor-teste",
                "password", "SenhaForte1"), JsonNode.class);
        token = body.get("accessToken").asText();
    }

    private ResponseEntity<String> upload(String auth, byte[] content, String filename) {
        var form = new LinkedMultiValueMap<String, Object>();
        form.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (auth != null) {
            headers.setBearerAuth(auth);
        }
        return rest.exchange("/api/v1/profile/photo", HttpMethod.POST,
                new HttpEntity<>(form, headers), String.class);
    }

    private JsonNode json(ResponseEntity<String> response) throws IOException {
        return objectMapper.readTree(response.getBody());
    }

    @Test
    @DisplayName("200: sobe a imagem, salva photo_url e cria o perfil que não existia")
    @SuppressWarnings("unchecked")
    void shouldUploadAndPersistPhotoUrl() throws Exception {
        when(uploader.upload(any(byte[].class), any(Map.class)))
                .thenReturn(Map.of("secure_url", URL));

        var response = upload(token, PNG, "foto.png");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(json(response).get("photoUrl").asText()).isEqualTo(URL);
        assertThat(jdbc.queryForObject("SELECT photo_url FROM user_profiles", String.class))
                .isEqualTo(URL);
        var options = ArgumentCaptor.forClass(Map.class);
        verify(uploader).upload(eq(PNG), options.capture());
        assertThat(options.getValue()).containsEntry("folder", "profiles");
    }

    @Test
    @DisplayName("200: segundo upload troca a URL no mesmo perfil e preserva os outros campos")
    void shouldReplacePhotoUrlKeepingProfile() throws Exception {
        when(uploader.upload(any(byte[].class), any(Map.class)))
                .thenReturn(Map.of("secure_url", URL))
                .thenReturn(Map.of("secure_url", URL + "2"));
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        rest.exchange("/api/v1/profile", HttpMethod.PUT,
                new HttpEntity<>(Map.of("bio", "Minha bio"), headers), String.class);

        upload(token, PNG, "a.png");
        upload(token, PNG, "b.png");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_profiles", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT photo_url FROM user_profiles", String.class))
                .isEqualTo(URL + "2");
        assertThat(jdbc.queryForObject("SELECT bio FROM user_profiles", String.class))
                .isEqualTo("Minha bio");
    }

    @Test
    @DisplayName("400: GIF e texto com extensão de imagem são recusados sem chamar o Cloudinary")
    void shouldRejectNonPngJpeg() throws Exception {
        var gif = upload(token, "GIF89a....".getBytes(), "foto.png");
        var text = upload(token, "isto nao e imagem".getBytes(), "foto.jpg");
        var corrupted = upload(token, TestImages.corruptedPng(), "foto.png");

        assertThat(gif.getStatusCode().value()).isEqualTo(400);
        assertThat(text.getStatusCode().value()).isEqualTo(400);
        assertThat(corrupted.getStatusCode().value()).isEqualTo(400);
        assertThat(json(gif).get("requestId")).isNotNull();
        verifyNoInteractions(uploader);
    }

    @Test
    @DisplayName("413: arquivo acima de 5 MB (limite real do Tomcat) em ProblemDetail")
    void shouldRejectFileOver5Mb() throws Exception {
        var big = new byte[5 * 1024 * 1024 + 1024];
        System.arraycopy(PNG, 0, big, 0, PNG.length);

        var response = upload(token, big, "grande.png");

        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(response.getHeaders().getContentType()).isNotNull()
                .hasToString("application/problem+json");
        assertThat(json(response).get("title").asText()).isEqualTo("Arquivo Muito Grande");
        verifyNoInteractions(uploader);
    }

    @Test
    @DisplayName("502: falha do Cloudinary vira ProblemDetail, sem gravar photo_url")
    void shouldReturn502WhenCloudinaryFails() throws Exception {
        when(uploader.upload(any(byte[].class), any(Map.class)))
                .thenThrow(new IOException("timeout", new SocketTimeoutException()));

        var response = upload(token, PNG, "foto.png");

        assertThat(response.getStatusCode().value()).isEqualTo(502);
        assertThat(json(response).get("title").asText()).isEqualTo("Falha em Serviço Externo");
        // O registro cria o perfil; a falha não pode ter gravado nenhuma URL
        assertThat(jdbc.queryForObject("SELECT count(photo_url) FROM user_profiles",
                Integer.class)).isZero();
    }

    @Test
    @DisplayName("401: sem token e com conta desativada (token ainda válido)")
    void shouldReturn401() {
        assertThat(upload(null, PNG, "foto.png").getStatusCode().value()).isEqualTo(401);

        jdbc.update("UPDATE users SET active = false");
        assertThat(upload(token, PNG, "foto.png").getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(uploader);
    }
}
