package com.autofolio.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.autofolio.config.CloudinaryProperties;
import com.autofolio.shared.exception.MediaStorageException;
import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.Uploader;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
@DisplayName("Testes Unitários - ProfilePhotoStorage")
class ProfilePhotoStorageTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final byte[] IMAGE = {1, 2, 3};

    @Mock
    private Cloudinary cloudinary;
    @Mock
    private Uploader uploader;
    private ProfilePhotoStorage storage;

    @BeforeEach
    void setUp() {
        when(cloudinary.uploader()).thenReturn(uploader);
        storage = new ProfilePhotoStorage(cloudinary,
                new CloudinaryProperties(Duration.ofMillis(111), Duration.ofMillis(222)));
    }

    @Test
    @DisplayName("Envia para profiles/ com crop=fill 400x400, id aleatório e timeouts explícitos")
    @SuppressWarnings("unchecked")
    void shouldUploadWithTransformationAndTimeouts() throws IOException {
        when(uploader.upload(eq(IMAGE), any(Map.class)))
                .thenReturn(Map.of("secure_url", "https://res.cloudinary.com/x/a.png"));

        var url = storage.upload(USER_ID, IMAGE);

        assertThat(url).isEqualTo("https://res.cloudinary.com/x/a.png");
        var captor = ArgumentCaptor.forClass(Map.class);
        verify(uploader).upload(eq(IMAGE), captor.capture());
        Map<String, Object> options = captor.getValue();
        assertThat(options).containsEntry("folder", "profiles")
                .containsEntry("connect_timeout", 111)
                .containsEntry("connection_request_timeout", 111)
                .containsEntry("timeout", 222);
        assertThat(UUID.fromString((String) options.get("public_id"))).isNotEqualTo(USER_ID);
        assertThat(((Transformation<?>) options.get("transformation")).generate())
                .isEqualTo("c_fill,h_400,w_400");
    }

    @Test
    @DisplayName("Timeout/IO do Cloudinary vira MediaStorageException e loga ERROR sem mensagem")
    void shouldWrapIoFailureAndLogWithoutMessage(CapturedOutput output) throws IOException {
        when(uploader.upload(eq(IMAGE), any(Map.class)))
                .thenThrow(new IOException("api_secret=SEGREDO",
                        new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> storage.upload(USER_ID, IMAGE))
                .isInstanceOf(MediaStorageException.class)
                .hasCauseInstanceOf(IOException.class);
        // A stacktrace (DEBUG, ADR-001 §1.7) pode conter a mensagem; a linha ERROR não
        var errorLine = output.getOut().lines().filter(line -> line.contains(" ERROR "))
                .findFirst().orElse("");
        assertThat(errorLine).contains("userId=" + USER_ID)
                .contains("IOException (causa raiz: SocketTimeoutException)")
                .doesNotContain("SEGREDO");
    }

    @Test
    @DisplayName("Falha em tempo de execução do SDK também vira MediaStorageException")
    void shouldWrapRuntimeFailure() throws IOException {
        when(uploader.upload(eq(IMAGE), any(Map.class))).thenThrow(new IllegalStateException());

        assertThatThrownBy(() -> storage.upload(USER_ID, IMAGE))
                .isInstanceOf(MediaStorageException.class);
    }

    @Test
    @DisplayName("Resposta sem URL https é tratada como falha")
    void shouldRejectResponseWithoutHttpsUrl() throws IOException {
        when(uploader.upload(eq(IMAGE), any(Map.class)))
                .thenReturn(Map.of("secure_url", "http://inseguro/a.png"));

        assertThatThrownBy(() -> storage.upload(USER_ID, IMAGE))
                .isInstanceOf(MediaStorageException.class);
    }
}
