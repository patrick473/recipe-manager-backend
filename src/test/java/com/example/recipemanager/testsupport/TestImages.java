package com.example.recipemanager.testsupport;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import javax.imageio.ImageIO;

/**
 * Real, minimal, valid image byte arrays for tests — generated (jpeg/png) or
 * decoded from a well-known tiny fixture (webp), rather than hand-rolled fake
 * bytes, so tests exercise the same {@code ImageIO}/signature-sniffing code
 * paths production traffic does.
 */
public final class TestImages {

    /**
     * A well-known minimal (1x1) valid WebP image. The JDK's built-in
     * {@code ImageIO} cannot decode WebP at all, so this app's content
     * sniffing verifies the RIFF/WEBP container signature instead — this
     * fixture carries a real one.
     */
    private static final String MINIMAL_WEBP_BASE64 = "UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==";

    private TestImages() {
    }

    public static byte[] jpegBytes() {
        return rasterBytes("jpg");
    }

    public static byte[] pngBytes() {
        return rasterBytes("png");
    }

    public static byte[] webpBytes() {
        return Base64.getDecoder().decode(MINIMAL_WEBP_BASE64);
    }

    private static byte[] rasterBytes(String formatName) {
        try {
            BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, formatName, out)) {
                throw new IllegalStateException("No ImageIO writer registered for format: " + formatName);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
