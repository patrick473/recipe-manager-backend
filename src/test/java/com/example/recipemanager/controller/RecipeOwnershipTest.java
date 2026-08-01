package com.example.recipemanager.controller;

import com.example.recipemanager.dto.AuthResponse;
import com.example.recipemanager.dto.RecipePageResponse;
import com.example.recipemanager.dto.RecipeRequest;
import com.example.recipemanager.dto.RecipeResponse;
import com.example.recipemanager.dto.RegisterRequest;
import com.example.recipemanager.testsupport.TestImages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Full-stack ownership test, in {@link RecipeControllerImageTest}'s style
 * (real embedded server, not a {@code @WebMvcTest} slice): registers two
 * separate accounts, has one create a recipe, and asserts the other account
 * can freely read that recipe (via {@code GET /recipes}, {@code GET
 * /recipes/{id}}, and its image) but can never edit, delete, or replace its
 * image — every such mutation attempt 404s exactly like a nonexistent id
 * would, per AUTHENTICATION_SPEC.md's "404, not 403" decision — while the
 * owning account's own requests against the same id succeed. Also covers the
 * outer boundary: a caller with no account at all can read the same three
 * endpoints but gets a 401 on every mutation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class RecipeOwnershipTest {

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

    private String tokenA;
    private String tokenB;
    private Long recipeIdOwnedByA;

    @BeforeEach
    void registerBothUsersAndCreateAsRecipe() {
        tokenA = register().getToken();
        tokenB = register().getToken();

        RecipeResponse created = createRecipe(tokenA, "A's Secret Recipe");
        recipeIdOwnedByA = created.getId();
    }

    private AuthResponse register() {
        String username = "ownership-test-" + UUID.randomUUID();
        RegisterRequest request = RegisterRequest.builder().username(username).password("password123").build();
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                url("/auth/register"), request, AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpEntity<Void> authorized(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private HttpEntity<RecipeRequest> authorizedBody(String token, RecipeRequest body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private RecipeResponse createRecipe(String token, String title) {
        RecipeRequest request = RecipeRequest.builder()
                .title(title)
                .content("## Ingredients\n- Secret stuff")
                .build();
        ResponseEntity<RecipeResponse> response = restTemplate.exchange(
                url("/recipes"), HttpMethod.POST, authorizedBody(token, request), RecipeResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private <T> ResponseEntity<T> uploadImage(String token, Long recipeId, Class<T> responseType) {
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
        requestHeaders.setBearerAuth(token);

        return restTemplate.exchange(
                url("/recipes/" + recipeId + "/image"), HttpMethod.POST,
                new HttpEntity<>(body, requestHeaders), responseType);
    }

    // -------------------------------------------------------------------------
    // B can read A's recipe, but every mutation attempt 404s
    // -------------------------------------------------------------------------

    @Test
    void bGettingAsRecipeSucceeds() {
        ResponseEntity<RecipeResponse> response = restTemplate.exchange(
                url("/recipes/" + recipeIdOwnedByA), HttpMethod.GET, authorized(tokenB), RecipeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(recipeIdOwnedByA);
        assertThat(response.getBody().getTitle()).isEqualTo("A's Secret Recipe");
    }

    @Test
    void bUpdatingAsRecipeReturns404() {
        RecipeRequest updateRequest = RecipeRequest.builder()
                .title("Hijacked title")
                .content("Hijacked content")
                .build();

        ResponseEntity<String> response = restTemplate.exchange(
                url("/recipes/" + recipeIdOwnedByA), HttpMethod.PUT,
                authorizedBody(tokenB, updateRequest), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("https://example.com/errors/recipe-not-found");
    }

    @Test
    void bDeletingAsRecipeReturns404() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/recipes/" + recipeIdOwnedByA), HttpMethod.DELETE, authorized(tokenB), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("https://example.com/errors/recipe-not-found");
    }

    @Test
    void bUploadingImageToAsRecipeReturns404() {
        ResponseEntity<String> response = uploadImage(tokenB, recipeIdOwnedByA, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("https://example.com/errors/recipe-not-found");
    }

    @Test
    void bGettingImageOfAsRecipeSucceeds() {
        ResponseEntity<RecipeResponse> uploadResponse = uploadImage(tokenA, recipeIdOwnedByA, RecipeResponse.class);
        assertThat(uploadResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<byte[]> response = restTemplate.exchange(
                url("/recipes/" + recipeIdOwnedByA + "/image"), HttpMethod.GET, authorized(tokenB), byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_JPEG);
    }

    @Test
    void bDeletingImageOfAsRecipeReturns404() {
        ResponseEntity<String> response = restTemplate.exchange(
                url("/recipes/" + recipeIdOwnedByA + "/image"), HttpMethod.DELETE, authorized(tokenB), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("https://example.com/errors/recipe-not-found");
    }

    @Test
    void bsRecipeListingIncludesAsRecipeToo() {
        createRecipe(tokenB, "B's Own Recipe");

        ResponseEntity<RecipePageResponse> response = restTemplate.exchange(
                url("/recipes?size=100"), HttpMethod.GET, authorized(tokenB), RecipePageResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent())
                .extracting(RecipeResponse::getId)
                .contains(recipeIdOwnedByA);
        assertThat(response.getBody().getContent())
                .extracting(RecipeResponse::getTitle)
                .contains("A's Secret Recipe");
    }

    // -------------------------------------------------------------------------
    // A's own requests against the same recipe succeed
    // -------------------------------------------------------------------------

    @Test
    void aGettingOwnRecipeSucceeds() {
        ResponseEntity<RecipeResponse> response = restTemplate.exchange(
                url("/recipes/" + recipeIdOwnedByA), HttpMethod.GET, authorized(tokenA), RecipeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(recipeIdOwnedByA);
    }

    @Test
    void aUpdatingOwnRecipeSucceeds() {
        RecipeRequest updateRequest = RecipeRequest.builder()
                .title("A's Updated Recipe")
                .content("Updated content")
                .build();

        ResponseEntity<RecipeResponse> response = restTemplate.exchange(
                url("/recipes/" + recipeIdOwnedByA), HttpMethod.PUT,
                authorizedBody(tokenA, updateRequest), RecipeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("A's Updated Recipe");
    }

    @Test
    void aDeletingOwnRecipeSucceeds() {
        ResponseEntity<Void> response = restTemplate.exchange(
                url("/recipes/" + recipeIdOwnedByA), HttpMethod.DELETE, authorized(tokenA), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void asRecipeListingIncludesTheirOwnRecipe() {
        ResponseEntity<RecipePageResponse> response = restTemplate.exchange(
                url("/recipes?size=100"), HttpMethod.GET, authorized(tokenA), RecipePageResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent())
                .extracting(RecipeResponse::getId)
                .contains(recipeIdOwnedByA);
    }

    // -------------------------------------------------------------------------
    // A caller with no account at all can read, but never mutate
    // -------------------------------------------------------------------------

    @Test
    void anonymousListingIncludesAsRecipe() {
        ResponseEntity<RecipePageResponse> response =
                restTemplate.getForEntity(url("/recipes?size=100"), RecipePageResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent())
                .extracting(RecipeResponse::getId)
                .contains(recipeIdOwnedByA);
    }

    @Test
    void anonymousGettingAsRecipeSucceeds() {
        ResponseEntity<RecipeResponse> response =
                restTemplate.getForEntity(url("/recipes/" + recipeIdOwnedByA), RecipeResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(recipeIdOwnedByA);
    }

    @Test
    void anonymousGettingImageOfAsRecipeSucceeds() {
        ResponseEntity<RecipeResponse> uploadResponse = uploadImage(tokenA, recipeIdOwnedByA, RecipeResponse.class);
        assertThat(uploadResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<byte[]> response =
                restTemplate.getForEntity(url("/recipes/" + recipeIdOwnedByA + "/image"), byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_JPEG);
    }

    @Test
    void anonymousCreatingRecipeReturns401() {
        RecipeRequest request = RecipeRequest.builder()
                .title("Anonymous Recipe")
                .content("## Ingredients\n- Nothing")
                .build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/recipes"), HttpMethod.POST, new HttpEntity<>(request, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anonymousUpdatingAsRecipeReturns401() {
        RecipeRequest updateRequest = RecipeRequest.builder()
                .title("Hijacked title")
                .content("Hijacked content")
                .build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/recipes/" + recipeIdOwnedByA), HttpMethod.PUT,
                new HttpEntity<>(updateRequest, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anonymousDeletingAsRecipeReturns401() {
        ResponseEntity<String> response =
                restTemplate.exchange(url("/recipes/" + recipeIdOwnedByA), HttpMethod.DELETE, null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
