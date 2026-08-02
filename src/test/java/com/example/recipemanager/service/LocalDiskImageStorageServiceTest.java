package com.example.recipemanager.service;

import com.example.recipemanager.testsupport.TestImages;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises {@link LocalDiskImageStorageService#store}/{@code delete}/{@code load}
 * against a real, temporary directory — this is exactly the kind of filesystem
 * side-effect logic that is subtly wrong if only unit-tested against mocks.
 */
class LocalDiskImageStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalDiskImageStorageService storageService() {
        return new LocalDiskImageStorageService(tempDir.toString());
    }

    @Test
    void storeWritesFileWithGeneratedUuidFilenameAndJpgExtension() throws IOException {
        LocalDiskImageStorageService service = storageService();
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", TestImages.jpegBytes());

        String filename = service.store(1L, file);

        assertThat(filename).endsWith(".jpg");
        Path stored = tempDir.resolve(filename);
        assertThat(Files.exists(stored)).isTrue();
        assertThat(Files.readAllBytes(stored)).isEqualTo(TestImages.jpegBytes());
    }

    @Test
    void storeDerivesExtensionFromSniffedFormatNotClientFilename() throws IOException {
        LocalDiskImageStorageService service = storageService();
        // Client-supplied filename lies (.png) about content that is actually a jpeg;
        // the stored filename must never be trusted from client input regardless.
        MockMultipartFile file = new MockMultipartFile(
                "file", "not-a-real-name.png", "image/jpeg", TestImages.jpegBytes());

        String filename = service.store(1L, file);

        assertThat(filename).endsWith(".jpg");
        assertThat(filename).doesNotContain("not-a-real-name");
    }

    @Test
    void storeSupportsPngAndWebp() throws IOException {
        LocalDiskImageStorageService service = storageService();

        String pngFilename = service.store(1L,
                new MockMultipartFile("file", "a.png", "image/png", TestImages.pngBytes()));
        String webpFilename = service.store(1L,
                new MockMultipartFile("file", "a.webp", "image/webp", TestImages.webpBytes()));

        assertThat(pngFilename).endsWith(".png");
        assertThat(webpFilename).endsWith(".webp");
    }

    @Test
    void generatesDistinctFilenamesForRepeatedUploads() throws IOException {
        LocalDiskImageStorageService service = storageService();
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", TestImages.jpegBytes());

        String first = service.store(1L, file);
        String second = service.store(1L, file);

        assertThat(first).isNotEqualTo(second);
        assertThat(Files.exists(tempDir.resolve(first))).isTrue();
        assertThat(Files.exists(tempDir.resolve(second))).isTrue();
    }

    @Test
    void deleteRemovesTheStoredFile() throws IOException {
        LocalDiskImageStorageService service = storageService();
        String filename = service.store(1L,
                new MockMultipartFile("file", "photo.jpg", "image/jpeg", TestImages.jpegBytes()));
        assertThat(Files.exists(tempDir.resolve(filename))).isTrue();

        service.delete(filename);

        assertThat(Files.exists(tempDir.resolve(filename))).isFalse();
    }

    @Test
    void deleteOfMissingFileDoesNotThrow() {
        LocalDiskImageStorageService service = storageService();

        service.delete("does-not-exist.jpg");
    }

    @Test
    void loadReturnsAResourceWithTheStoredBytes() throws IOException {
        LocalDiskImageStorageService service = storageService();
        byte[] bytes = TestImages.pngBytes();
        String filename = service.store(1L, new MockMultipartFile("file", "photo.png", "image/png", bytes));

        Resource resource = service.load(filename);

        try (InputStream in = resource.getInputStream()) {
            assertThat(in.readAllBytes()).isEqualTo(bytes);
        }
    }

    @Test
    void loadRejectsPathTraversalFilename() {
        LocalDiskImageStorageService service = storageService();

        assertThatThrownBy(() -> service.load("../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loadRejectsFilenameContainingPathSeparator() {
        LocalDiskImageStorageService service = storageService();

        assertThatThrownBy(() -> service.load("sub/photo.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.load("sub\\photo.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteRejectsPathTraversalFilename() {
        LocalDiskImageStorageService service = storageService();

        assertThatThrownBy(() -> service.delete("../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteRejectsFilenameContainingPathSeparator() {
        LocalDiskImageStorageService service = storageService();

        assertThatThrownBy(() -> service.delete("sub/photo.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.delete("sub\\photo.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void storeCreatesUploadDirectoryIfMissing() throws IOException {
        Path nestedDir = tempDir.resolve("nested/upload/dir");
        LocalDiskImageStorageService service = new LocalDiskImageStorageService(nestedDir.toString());

        String filename = service.store(1L,
                new MockMultipartFile("file", "photo.jpg", "image/jpeg", TestImages.jpegBytes()));

        assertThat(Files.exists(nestedDir.resolve(filename))).isTrue();
    }
}
