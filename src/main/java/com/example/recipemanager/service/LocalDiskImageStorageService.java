package com.example.recipemanager.service;

import com.example.recipemanager.exception.InvalidImageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

/**
 * {@link ImageStorageService} backed by the local filesystem, rooted at
 * {@code app.storage.upload-dir} (relative to the working directory the
 * backend is started from unless given as an absolute path).
 */
@Service
public class LocalDiskImageStorageService implements ImageStorageService {

    private final Path uploadDir;

    public LocalDiskImageStorageService(@Value("${app.storage.upload-dir:uploads}") String uploadDir) {
        this.uploadDir = Path.of(uploadDir);
    }

    @Override
    public String store(Long recipeId, MultipartFile file) {
        try {
            Files.createDirectories(uploadDir);
            String filename = UUID.randomUUID() + "." + detectExtension(file);
            Path target = uploadDir.resolve(filename);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return filename;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store image for recipe " + recipeId, e);
        }
    }

    @Override
    public void delete(String filename) {
        validateFilename(filename);
        try {
            Files.deleteIfExists(uploadDir.resolve(filename));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete image file: " + filename, e);
        }
    }

    @Override
    public Resource load(String filename) {
        validateFilename(filename);
        return new FileSystemResource(uploadDir.resolve(filename));
    }

    /**
     * Rejects filenames that could escape {@link #uploadDir} when resolved
     * (e.g. via {@code ..} traversal or a path separator). Every current
     * caller passes a server-generated UUID-based filename, so this is
     * defense-in-depth against a future caller threading user input through.
     */
    private static void validateFilename(String filename) {
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            throw new IllegalArgumentException("Invalid image filename: " + filename);
        }
    }

    /**
     * Determines the file extension from the sniffed image format (never the
     * client-supplied filename), by letting {@link ImageIO} identify a reader
     * for the actual bytes. Since {@link com.example.recipemanager.service.RecipeService}
     * has already confirmed the bytes match their declared content type before
     * calling {@link #store}, a reader is always found in practice — except for
     * WebP, which the JDK's built-in {@code ImageIO} cannot decode at all, so
     * that case is derived directly from the (already-validated) declared
     * content type instead of re-sniffing.
     */
    private String detectExtension(MultipartFile file) throws IOException {
        if ("image/webp".equalsIgnoreCase(file.getContentType())) {
            return "webp";
        }
        try (InputStream rawIn = file.getInputStream();
                ImageInputStream iis = ImageIO.createImageInputStream(rawIn)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (readers.hasNext()) {
                return normalizeExtension(readers.next().getFormatName());
            }
        }
        throw new InvalidImageException("Unable to determine image format");
    }

    private static String normalizeExtension(String formatName) {
        return switch (formatName.toUpperCase(Locale.ROOT)) {
            case "JPEG", "JPG" -> "jpg";
            case "PNG" -> "png";
            case "WEBP" -> "webp";
            default -> formatName.toLowerCase(Locale.ROOT);
        };
    }
}
