package com.autofolio.shared.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.autofolio.shared.exception.InvalidImageException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@DisplayName("Testes Unitários - ImageValidator")
class ImageValidatorTest {

    private static MockMultipartFile file(byte[] content) {
        return new MockMultipartFile("file", "foto.png", "image/png", content);
    }

    @Test
    @DisplayName("Aceita PNG e JPEG reais e devolve o conteúdo")
    void shouldAcceptRealImages() {
        assertThat(ImageValidator.readAndValidate(file(TestImages.png())))
                .isEqualTo(TestImages.png());
        assertThatCode(() -> ImageValidator.validate(TestImages.jpeg()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Recusa GIF, texto e assinatura PNG truncada (magic bytes)")
    void shouldRejectOtherFormats() {
        for (var content : new byte[][] {"GIF89a....".getBytes(StandardCharsets.US_ASCII),
                "<html>".getBytes(StandardCharsets.US_ASCII), {(byte) 0x89, 'P', 'N'}}) {
            assertThatThrownBy(() -> ImageValidator.validate(content))
                    .isInstanceOf(InvalidImageException.class)
                    .hasMessage(ImageValidator.FORMAT_MESSAGE);
        }
    }

    @Test
    @DisplayName("Recusa PNG e JPEG com assinatura válida e corpo corrompido")
    void shouldRejectCorruptedBody() {
        for (var content : new byte[][] {TestImages.corruptedPng(), TestImages.corruptedJpeg()}) {
            assertThatThrownBy(() -> ImageValidator.validate(content))
                    .isInstanceOf(InvalidImageException.class)
                    .hasMessage(ImageValidator.UNREADABLE_MESSAGE);
        }
    }

    @Test
    @DisplayName("Recusa arquivo vazio")
    void shouldRejectEmptyFile() {
        assertThatThrownBy(() -> ImageValidator.readAndValidate(file(new byte[0])))
                .isInstanceOf(InvalidImageException.class)
                .hasMessage(ImageValidator.EMPTY_MESSAGE);
    }

    @Test
    @DisplayName("Falha de leitura do arquivo vira UncheckedIOException")
    void shouldFailWhenFileCannotBeRead() throws Exception {
        var broken = mock(MultipartFile.class);
        when(broken.isEmpty()).thenReturn(false);
        when(broken.getBytes()).thenThrow(new IOException("disco"));

        assertThatThrownBy(() -> ImageValidator.readAndValidate(broken))
                .isInstanceOf(UncheckedIOException.class);
    }
}
