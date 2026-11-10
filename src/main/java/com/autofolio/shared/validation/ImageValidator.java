package com.autofolio.shared.validation;

import com.autofolio.shared.exception.InvalidImageException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.web.multipart.MultipartFile;

/**
 * Valida uploads de imagem PNG ou JPEG pelo conteúdo real, não pela extensão nem pelo
 * Content-Type: magic bytes e, depois, leitura do cabeçalho com {@code javax.imageio} (sem
 * decodificar a imagem inteira). Assim, arquivo corrompido vira 400 do cliente em vez de falha
 * no serviço externo.
 */
public final class ImageValidator {

    /** Mensagem para arquivo ausente ou vazio. */
    public static final String EMPTY_MESSAGE = "Envie uma imagem PNG, JPG ou JPEG.";
    /** Mensagem para formato fora de PNG/JPEG. */
    public static final String FORMAT_MESSAGE =
            "Formato inválido: envie uma imagem PNG, JPG ou JPEG.";
    /** Mensagem para imagem com cabeçalho ilegível. */
    public static final String UNREADABLE_MESSAGE =
            "Imagem corrompida ou ilegível: envie outro arquivo PNG, JPG ou JPEG.";

    private static final byte[] PNG_MAGIC =
            {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private ImageValidator() {
    }

    /**
     * Lê o arquivo e o valida.
     *
     * @return o conteúdo do arquivo
     * @throws InvalidImageException se vazio, de outro formato ou com cabeçalho ilegível
     */
    public static byte[] readAndValidate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidImageException(EMPTY_MESSAGE);
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o arquivo enviado", e);
        }
        validate(content);
        return content;
    }

    /**
     * Valida o conteúdo.
     *
     * @throws InvalidImageException se de outro formato ou com cabeçalho ilegível
     */
    public static void validate(byte[] content) {
        if (hasUnknownSignature(content)) {
            throw new InvalidImageException(FORMAT_MESSAGE);
        }
        if (!hasReadableHeader(content)) {
            throw new InvalidImageException(UNREADABLE_MESSAGE);
        }
    }

    // Só o cabeçalho (dimensões); o stream em memória evita arquivo temporário do ImageIO
    private static boolean hasReadableHeader(byte[] content) {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(content))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return false;
            }
            var reader = readers.next();
            try {
                reader.setInput(input);
                return reader.getWidth(0) > 0 && reader.getHeight(0) > 0;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static boolean hasUnknownSignature(byte[] content) {
        return Stream.of(PNG_MAGIC, JPEG_MAGIC).noneMatch(magic -> content.length >= magic.length
                && Arrays.equals(content, 0, magic.length, magic, 0, magic.length));
    }
}
