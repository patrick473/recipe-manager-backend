package com.example.recipemanager.controller;

import com.example.recipemanager.dto.AuthResponse;
import com.example.recipemanager.dto.RecipeResponse;
import com.example.recipemanager.dto.RegisterRequest;
import com.example.recipemanager.model.Recipe;
import com.example.recipemanager.model.User;
import com.example.recipemanager.repository.RecipeRepository;
import com.example.recipemanager.repository.UserRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests for the three image endpoints, exercised over a real
 * embedded server (not a {@code @WebMvcTest} MockMvc slice) because
 * {@code spring.servlet.multipart.max-file-size} is enforced during genuine
 * servlet-container multipart parsing — {@code MockMvcRequestBuilders.multipart()}
 * pre-builds a {@code MockMultipartHttpServletRequest} that bypasses that
 * parsing entirely, so it can never exercise the oversized-upload rejection.
 * <p>
 * Uploading, replacing, and deleting the image require a valid
 * {@code Authorization: Bearer <token>} header, while {@code GET
 * /recipes/{id}/image} is public (see AUTHENTICATION_SPEC.md Part 3), so each
 * test registers a fresh account, attaches the issued token to every
 * mutating request, and owns the fixture recipe under that same account.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class RecipeControllerImageTest {

    @TempDir
    static Path tempUploadDir;

    @DynamicPropertySource
    static void overrideUploadDir(DynamicPropertyRegistry registry) {
        registry.add("app.storage.upload-dir", () -> tempUploadDir.toString());
        // JwtService now refuses to construct with the shipped
        // application.properties default (see JwtService.SHIPPED_DEFAULT_SECRET);
        // this is a real embedded-server context load, so it needs its own secret.
        registry.add("app.jwt.secret", () -> "93nVNqbyLh/RSvsAb1FIlGeVkimlTQ8WxAvLWegsAsQ=");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RecipeRepository repository;

    @Autowired
    private UserRepository userRepository;

    private Long recipeId;
    private String token;

    @BeforeEach
    void createRecipe() {
        AuthResponse auth = register();
        User owner = userRepository.findById(auth.getUserId()).orElseThrow();
        token = auth.getToken();

        Recipe saved = repository.save(Recipe.builder()
                .title("Banana Bread")
                .description("Moist and simple")
                .content("## Ingredients\n- Bananas")
                .owner(owner)
                .build());
        recipeId = saved.getId();
    }

    private AuthResponse register() {
        String username = "image-test-" + UUID.randomUUID();
        RegisterRequest request = RegisterRequest.builder().username(username).password("password123").build();
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/auth/register", request, AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
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
        requestHeaders.setBearerAuth(token);
        return new HttpEntity<>(body, requestHeaders);
    }

    private HttpEntity<Void> authorizedEmpty() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
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
        assertThat(response.getBody().getImageUrl()).matches("/recipes/" + recipeId + "/image\\?v=.+");
    }

    @Test
    void uploadingValidPngSucceedsAndImageUrlAppearsInResponse() {
        ResponseEntity<RecipeResponse> response =
                upload(recipeId, TestImages.pngBytes(), "photo.png", MediaType.IMAGE_PNG);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getImageUrl()).matches("/recipes/" + recipeId + "/image\\?v=.+");
    }

    @Test
    void uploadingValidWebpSucceedsAndImageUrlAppearsInResponse() {
        ResponseEntity<RecipeResponse> response =
                upload(recipeId, TestImages.webpBytes(), "photo.webp", MediaType.valueOf("image/webp"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getImageUrl()).matches("/recipes/" + recipeId + "/image\\?v=.+");
    }

    @Test
    void replacingAnImageChangesTheVersionQueryParamButNotThePath() {
        ResponseEntity<RecipeResponse> firstUpload =
                upload(recipeId, TestImages.jpegBytes(), "photo.jpg", MediaType.IMAGE_JPEG);
        assertThat(firstUpload.getStatusCode()).isEqualTo(HttpStatus.OK);
        String firstImageUrl = firstUpload.getBody().getImageUrl();

        ResponseEntity<RecipeResponse> secondUpload =
                upload(recipeId, TestImages.pngBytes(), "photo.png", MediaType.IMAGE_PNG);
        assertThat(secondUpload.getStatusCode()).isEqualTo(HttpStatus.OK);
        String secondImageUrl = secondUpload.getBody().getImageUrl();

        String expectedPath = "/recipes/" + recipeId + "/image";
        assertThat(firstImageUrl).startsWith(expectedPath + "?v=");
        assertThat(secondImageUrl).startsWith(expectedPath + "?v=");
        assertThat(secondImageUrl).isNotEqualTo(firstImageUrl);

        String firstVersion = firstImageUrl.substring(firstImageUrl.indexOf("?v=") + 3);
        String secondVersion = secondImageUrl.substring(secondImageUrl.indexOf("?v=") + 3);
        assertThat(secondVersion).isNotEqualTo(firstVersion);
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

        ResponseEntity<byte[]> response = restTemplate.exchange(
                imageUrl(recipeId), HttpMethod.GET, authorizedEmpty(), byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(response.getHeaders().getCacheControl()).contains("max-age=31536000").contains("immutable");
        assertThat(response.getHeaders().getETag()).isNotBlank();
        assertThat(response.getBody()).isEqualTo(pngBytes);
    }

    @Test
    void gettingImageOfRecipeWithNoImageReturns404() {
        ResponseEntity<String> response = restTemplate.exchange(
                imageUrl(recipeId), HttpMethod.GET, authorizedEmpty(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("https://example.com/errors/image-not-found");
    }

    @Test
    void gettingImageOfNonexistentRecipeReturns404() {
        ResponseEntity<String> response = restTemplate.exchange(
                imageUrl(999_999L), HttpMethod.GET, authorizedEmpty(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("https://example.com/errors/image-not-found");
    }

    @Test
    void gettingImageWithNoTokenSucceeds() {
        byte[] pngBytes = TestImages.pngBytes();
        upload(recipeId, pngBytes, "photo.png", MediaType.IMAGE_PNG);

        ResponseEntity<byte[]> response = restTemplate.getForEntity(imageUrl(recipeId), byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(pngBytes);
    }

    @Test
    void uploadingImageWithNoTokenReturns401() {
        ByteArrayResource resource = new ByteArrayResource(TestImages.jpegBytes()) {
            @Override
            public String getFilename() {
                return "photo.jpg";
            }
        };
        HttpHeaders filePartHeaders = new HttpHeaders();
        filePartHeaders.setContentType(MediaType.IMAGE_JPEG);
        HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(resource, filePartHeaders);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", filePart);

        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<String> response = restTemplate.exchange(
                imageUrl(recipeId), HttpMethod.POST, new HttpEntity<>(body, requestHeaders), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("https://example.com/errors/unauthorized");
    }

    // -------------------------------------------------------------------------
    // DELETE /recipes/{id}/image
    // -------------------------------------------------------------------------

    @Test
    void deletingImageOfRecipeWithImageRemovesItAndReturns200() {
        upload(recipeId, TestImages.jpegBytes(), "photo.jpg", MediaType.IMAGE_JPEG);

        ResponseEntity<RecipeResponse> deleteResponse = restTemplate.exchange(
                imageUrl(recipeId), HttpMethod.DELETE, authorizedEmpty(), RecipeResponse.class);

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(deleteResponse.getBody()).isNotNull();
        assertThat(deleteResponse.getBody().getImageUrl()).isNull();

        ResponseEntity<String> getResponse = restTemplate.exchange(
                imageUrl(recipeId), HttpMethod.GET, authorizedEmpty(), String.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deletingImageOfRecipeWithNoImageIsANoOpReturning200() {
        ResponseEntity<RecipeResponse> response = restTemplate.exchange(
                imageUrl(recipeId), HttpMethod.DELETE, authorizedEmpty(), RecipeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getImageUrl()).isNull();
    }

    @Test
    void deletingImageOfNonexistentRecipeReturns404() {
        ResponseEntity<String> response = restTemplate.exchange(
                imageUrl(999_999L), HttpMethod.DELETE, authorizedEmpty(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("https://example.com/errors/recipe-not-found");
    }
}
