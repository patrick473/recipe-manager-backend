package com.example.recipemanager.service;

import com.example.recipemanager.dto.RecipeResponse;
import com.example.recipemanager.exception.ImageNotFoundException;
import com.example.recipemanager.exception.InvalidImageException;
import com.example.recipemanager.exception.RecipeNotFoundException;
import com.example.recipemanager.model.Recipe;
import com.example.recipemanager.model.User;
import com.example.recipemanager.repository.RecipeRepository;
import com.example.recipemanager.repository.UserRepository;
import com.example.recipemanager.testsupport.TestImages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RecipeService}'s image-related methods:
 * {@code uploadImage}, {@code deleteImage}, {@code loadImage}. The 404/400
 * paths and the "replace deletes the old file" behavior are the parts most
 * likely to regress silently, so they get direct coverage here rather than
 * relying solely on {@code RecipeControllerImageTest}.
 */
@ExtendWith(MockitoExtension.class)
class RecipeServiceImageTest {

    private static final Long OWNER_ID = 1L;

    @Mock
    private RecipeRepository repository;

    @Mock
    private ImageStorageService imageStorageService;

    @Mock
    private UserRepository userRepository;

    private RecipeService service;

    @BeforeEach
    void setUp() {
        service = new RecipeService(repository, imageStorageService, userRepository);
    }

    private static Recipe recipeWithId(Long id) {
        return Recipe.builder()
                .id(id)
                .title("Banana Bread")
                .description("Moist and simple")
                .content("content")
                .owner(User.builder().id(OWNER_ID).username("owner").password("hash").build())
                .build();
    }

    // -------------------------------------------------------------------------
    // uploadImage
    // -------------------------------------------------------------------------

    @Test
    void uploadImageStoresFileAndSetsImageFilenameOnFirstUpload() {
        Recipe entity = recipeWithId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(imageStorageService.store(eq(1L), any())).thenReturn("generated-uuid.jpg");
        when(repository.save(any(Recipe.class))).thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", TestImages.jpegBytes());
        RecipeResponse response = service.uploadImage(1L, file, OWNER_ID);

        assertThat(entity.getImageFilename()).isEqualTo("generated-uuid.jpg");
        assertThat(response.getImageUrl()).isEqualTo("/recipes/1/image?v=generated-uuid.jpg");
        verify(imageStorageService, never()).delete(any());
    }

    @Test
    void uploadImageReplacingAnExistingOneDeletesTheOldFileNotOrphaningIt() {
        Recipe entity = recipeWithId(1L);
        entity.setImageFilename("old-uuid.jpg");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(imageStorageService.store(eq(1L), any())).thenReturn("new-uuid.png");
        when(repository.save(any(Recipe.class))).thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", TestImages.pngBytes());
        RecipeResponse response = service.uploadImage(1L, file, OWNER_ID);

        verify(imageStorageService).delete("old-uuid.jpg");
        assertThat(entity.getImageFilename()).isEqualTo("new-uuid.png");
        assertThat(response.getImageUrl()).isEqualTo("/recipes/1/image?v=new-uuid.png");
    }

    @Test
    void uploadImageAcceptsJpegPngAndWebp() {
        Recipe entity = recipeWithId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(imageStorageService.store(anyLong(), any())).thenReturn("uuid.ext");
        when(repository.save(any(Recipe.class))).thenAnswer(inv -> inv.getArgument(0));

        service.uploadImage(1L, new MockMultipartFile("file", "a.jpg", "image/jpeg", TestImages.jpegBytes()), OWNER_ID);
        service.uploadImage(1L, new MockMultipartFile("file", "a.png", "image/png", TestImages.pngBytes()), OWNER_ID);
        service.uploadImage(1L, new MockMultipartFile("file", "a.webp", "image/webp", TestImages.webpBytes()), OWNER_ID);
    }

    @Test
    void uploadImageOnNonexistentRecipeThrows404() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", TestImages.jpegBytes());

        assertThatThrownBy(() -> service.uploadImage(99L, file, OWNER_ID))
                .isInstanceOf(RecipeNotFoundException.class);

        verifyNoInteractions(imageStorageService);
    }

    @Test
    void uploadImageRejectsUnsupportedContentType() {
        Recipe entity = recipeWithId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        MockMultipartFile file = new MockMultipartFile("file", "photo.gif", "image/gif", TestImages.pngBytes());

        assertThatThrownBy(() -> service.uploadImage(1L, file, OWNER_ID))
                .isInstanceOf(InvalidImageException.class);

        verifyNoInteractions(imageStorageService);
    }

    @Test
    void uploadImageRejectsBytesThatDoNotDecodeAsAnImageEvenWithSpoofedContentType() {
        Recipe entity = recipeWithId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        MockMultipartFile file = new MockMultipartFile(
                "file", "not-an-image.jpg", "image/jpeg", "just some plain text".getBytes());

        assertThatThrownBy(() -> service.uploadImage(1L, file, OWNER_ID))
                .isInstanceOf(InvalidImageException.class);

        verifyNoInteractions(imageStorageService);
    }

    // -------------------------------------------------------------------------
    // deleteImage
    // -------------------------------------------------------------------------

    @Test
    void deleteImageOnRecipeWithImageDeletesFileAndClearsField() {
        Recipe entity = recipeWithId(1L);
        entity.setImageFilename("uuid.jpg");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any(Recipe.class))).thenAnswer(inv -> inv.getArgument(0));

        RecipeResponse response = service.deleteImage(1L, OWNER_ID);

        verify(imageStorageService).delete("uuid.jpg");
        assertThat(entity.getImageFilename()).isNull();
        assertThat(response.getImageUrl()).isNull();
    }

    @Test
    void deleteImageOnRecipeWithNoImageIsANoOpNotAnError() {
        Recipe entity = recipeWithId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any(Recipe.class))).thenAnswer(inv -> inv.getArgument(0));

        RecipeResponse response = service.deleteImage(1L, OWNER_ID);

        verifyNoInteractions(imageStorageService);
        assertThat(response.getImageUrl()).isNull();
        verify(repository).save(entity);
    }

    @Test
    void deleteImageOnNonexistentRecipeThrows404() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteImage(99L, OWNER_ID))
                .isInstanceOf(RecipeNotFoundException.class);

        verifyNoInteractions(imageStorageService);
    }

    // -------------------------------------------------------------------------
    // loadImage
    // -------------------------------------------------------------------------

    @Test
    void loadImageReturnsResourceAndHeadersForRecipeWithImage() {
        Recipe entity = recipeWithId(1L);
        entity.setImageFilename("uuid.png");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        Resource resource = mock(Resource.class);
        when(imageStorageService.load("uuid.png")).thenReturn(resource);

        RecipeImage image = service.loadImage(1L);

        assertThat(image.resource()).isSameAs(resource);
        assertThat(image.contentType().toString()).isEqualTo("image/png");
        assertThat(image.eTag()).isEqualTo("\"uuid.png\"");
    }

    @Test
    void loadImageOnRecipeWithNoImageThrows404() {
        Recipe entity = recipeWithId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.loadImage(1L))
                .isInstanceOf(ImageNotFoundException.class);

        verifyNoInteractions(imageStorageService);
    }

    @Test
    void loadImageOnNonexistentRecipeThrows404() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadImage(99L))
                .isInstanceOf(ImageNotFoundException.class);

        verifyNoInteractions(imageStorageService);
    }
}
