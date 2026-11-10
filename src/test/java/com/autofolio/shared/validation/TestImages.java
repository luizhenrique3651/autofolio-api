package com.autofolio.shared.validation;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import javax.imageio.ImageIO;

/**
 * Imagens reais mínimas para os testes de upload.
 */
public final class TestImages {

    private TestImages() {
    }

    /** PNG válido de 2x2 pixels. */
    public static byte[] png() {
        return encode("png");
    }

    /** JPEG válido de 2x2 pixels. */
    public static byte[] jpeg() {
        return encode("jpg");
    }

    /** Assinatura PNG válida seguida de lixo: passa nos magic bytes, mas é ilegível. */
    public static byte[] corruptedPng() {
        var content = new byte[64];
        System.arraycopy(png(), 0, content, 0, 8);
        Arrays.fill(content, 8, content.length, (byte) 0x7F);
        return content;
    }

    /** Assinatura JPEG válida seguida de lixo. */
    public static byte[] corruptedJpeg() {
        var content = new byte[64];
        System.arraycopy(jpeg(), 0, content, 0, 3);
        Arrays.fill(content, 3, content.length, (byte) 0x7F);
        return content;
    }

    private static byte[] encode(String format) {
        var out = new ByteArrayOutputStream();
        try {
            ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
