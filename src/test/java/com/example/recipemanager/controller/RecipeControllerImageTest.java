package com.example.recipemanager.controller;

import com.example.recipemanager.dto.RecipeResponse;
import com.example.recipemanager.model.Recipe;
import com.example.recipemanager.repository.RecipeRepository;
import com.example.recipemanager.testsupport.TestImages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests for the three image endpoints, exercised over a real
 * embedded server (not a {@code @WebMvcTest} MockMvc slice) because
 * {@code spring.servlet.multipart.max-file-size} is enforced during genuine
 * servlet-container multipart parsing — {@code MockMvcRequestBuilders.multipart()}
 * pre-builds a {@code MockMultipartHttpServletRequest} that bypasses that
 * parsing entirely, so it can never exercise the oversized-upload rejection.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class RecipeControllerImageTest {

    @TempDir
    static Path tempUploadDir;

    @DynamicPropertySource
    static void overrideUploadDir(DynamicPropertyRegistry registry) {
        registry.add("app.storage.upload-dir", () -> tempUploadDir.toString());
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RecipeRepository repository;

    private Long recipeId;

    @BeforeEach
    void createRecipe() {
        Recipe saved = repository.save(Recipe.builder()
                .title("Banana Bread")
                .description("Moist and simple")
                .content("## Ingredients\n- Bananas")
                .build());
        recipeId = saved.getId();
    }

    private String imageUrl(Long id) {
        return "http://localhost:" + port + "/recipes/" + id + "/image";
    }

    private ResponseEntity<RecipeResponse> upload(Long id, byte[] bytes, String filename, MediaType contentType) {
        return restTemplate.exchange(
                imageUrl(id), HttpMethod.POST, multipartRequest(bytes, filename, contentType), RecipeResponse.class);
    }

    private HttpEntity<MultiValueMap<String, Object>> multipartRequest(
            byte[] bytes, String filename, MediaType contentType) {
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        HttpHeaders filePartHeaders = new HttpHeaders();
        filePartHeaders.setContentType(contentType);
        HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(resource, filePartHeaders);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", filePart);

        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        return new HttpEntity<>(body, requestHeaders);
    }

    // -------------------------------------------------------------------------
    // POST /recipes/{id}/image
    // -------------------------------------------------------------------------

    @Test
    void uploadingValidJpegSucceedsAndImageUrlAppearsInResponse() {
        ResponseEntity<RecipeResponse> response =
                upload(recipeId, TestImages.jpegBytes(), "photo.jpg", MediaType.IMAGE_JPEG);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getImageUrl()).isEqualTo("/recipes/" + recipeId + "/image");
    }

    @Test
    void uploadingValidPngSucceedsAndImageUrlAppearsInResponse() {
        ResponseEntity<RecipeResponse> response =
                upload(recipeId, TestImages.pngBytes(), "photo.png", MediaType.IMAGE_PNG);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getImageUrl()).isEqualTo("/recipes/" + recipeId + "/image");
    }

    @Test
    void uploadingValidWebpSucceedsAndImageUrlAppearsInResponse() {
        ResponseEntity<RecipeResponse> response =
                upload(recipeId, TestImages.webpBytes(), "photo.webp", MediaType.valueOf("image/webp"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getImageUrl()).isEqualTo("/recipes/" + recipeId + "/image");
    }

    @Test
    void uploadingOversizedFileReturns400() {
        byte[] oversized = new byte[6 * 1024 * 1024]; // 6MB, above the configured 5MB limit

        ResponseEntity<String> response = restTemplate.exchange(
                imageUrl(recipeId), HttpMethod.POST,
                multipartRequest(oversized, "big.jpg", MediaType.IMAGE_JPEG), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("https://example.com/errors/image-too-large");
    }

    @Test
    void uploadingNonImageBytesWithSpoofedImageContentTypeReturns400() {
        byte[] notAnImage = "this is definitely not image data".getBytes();

        ResponseEntity<String> response = restTemplate.exchange(
                imageUrl(recipeId), HttpMethod.POST,
                multipartRequest(notAnImage, "fake.jpg", MediaType.IMAGE_JPEG), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("https://example.com/errors/invalid-image");
    }

    @Test
    void uploadingWithUnsupportedContentTypeReturns400() {
        ResponseEntity<String> response = restTemplate.exchange(
                imageUrl(recipeId), HttpMethod.POST,
                multipartRequest(TestImages.pngBytes(), "photo.gif", MediaType.IMAGE_GIF), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("https://example.com/errors/invalid-image");
    }

    @Test
    void uploadingImageForNonexistentRecipeReturns404() {
        ResponseEntity<String> response = restTemplate.exchange(
                imageUrl(999_999L), HttpMethod.POST,
                multipartRequest(TestImages.jpegBytes(), "photo.jpg", MediaType.IMAGE_JPEG), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("https://example.com/errors/recipe-not-found");
    }

    // -------------------------------------------------------------------------
    // GET /recipes/{id}/image
    // -------------------------------------------------------------------------

    @Test
    void gettingImageOfRecipeWithImageReturnsCorrectContentTypeAndBytes() {
        byte[] pngBytes = TestImages.pngBytes();
        upload(recipeId, pngBytes, "photo.png", MediaType.IMAGE_PNG);

        ResponseEntity<byte[]> response = restTemplate.getForEntity(imageUrl(recipeId), byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(response.getHeaders().getCacheControl()).contains("max-age=31536000").contains("immutable");
        assertThat(response.getHeaders().getETag()).isNotBlank();
        assertThat(response.getBody()).isEqualTo(pngBytes);
    }

    @Test
    void gettingImageOfRecipeWithNoImageReturns404() {
        ResponseEntity<String> response = restTemplate.getForEntity(imageUrl(recipeId), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("https://example.com/errors/image-not-found");
    }

    @Test
    void gettingImageOfNonexistentRecipeReturns404() {
        ResponseEntity<String> response = restTemplate.getForEntity(imageUrl(999_999L), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("https://example.com/errors/image-not-found");
    }

    // -------------------------------------------------------------------------
    // DELETE /recipes/{id}/image
    // -------------------------------------------------------------------------

    @Test
    void deletingImageOfRecipeWithImageRemovesItAndReturns200() {
        upload(recipeId, TestImages.jpegBytes(), "photo.jpg", MediaType.IMAGE_JPEG);

        ResponseEntity<RecipeResponse> deleteResponse = restTemplate.exchange(
                imageUrl(recipeId), HttpMethod.DELETE, HttpEntity.EMPTY, RecipeResponse.class);

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(deleteResponse.getBody()).isNotNull();
        assertThat(deleteResponse.getBody().getImageUrl()).isNull();

        ResponseEntity<String> getResponse = restTemplate.getForEntity(imageUrl(recipeId), String.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deletingImageOfRecipeWithNoImageIsANoOpReturning200() {
        ResponseEntity<RecipeResponse> response = restTemplate.exchange(
                imageUrl(recipeId), HttpMethod.DELETE, HttpEntity.EMPTY, RecipeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getImageUrl()).isNull();
    }

    @Test
    void deletingImageOfNonexistentRecipeReturns404() {
        ResponseEntity<String> response = restTemplate.exchange(
                imageUrl(999_999L), HttpMethod.DELETE, HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("https://example.com/errors/recipe-not-found");
    }
}
