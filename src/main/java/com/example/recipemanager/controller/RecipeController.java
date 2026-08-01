package com.example.recipemanager.controller;

import com.example.recipemanager.dto.RecipePageResponse;
import com.example.recipemanager.dto.RecipeRequest;
import com.example.recipemanager.dto.RecipeResponse;
import com.example.recipemanager.exception.InvalidSortFieldException;
import com.example.recipemanager.security.UserPrincipal;
import com.example.recipemanager.service.RecipeImage;
import com.example.recipemanager.service.RecipeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.Explode;
import io.swagger.v3.oas.annotations.enums.ParameterStyle;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * REST controller exposing the five CRUD endpoints for recipes.
 *
 * <pre>
 * GET    /recipes         — list all recipes
 * GET    /recipes/{id}    — get a single recipe
 * POST   /recipes         — create a new recipe
 * PUT    /recipes/{id}    — replace an existing recipe
 * DELETE /recipes/{id}    — remove a recipe
 * </pre>
 */
@RestController
@RequestMapping(value = "/recipes", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Recipes", description = "Create, read, update, and delete Markdown-based recipes")
public class RecipeController {

    private final RecipeService service;

    /** Sort fields allowed on {@code GET /recipes}'s {@code sort} query parameter. */
    private static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("title", "prepTimeMinutes", "cookTimeMinutes", "createdAt", "updatedAt");

    /** Fields whose sort order should always place {@code null} values last, regardless of direction. */
    private static final Set<String> NULLS_LAST_SORT_FIELDS = Set.of("prepTimeMinutes", "cookTimeMinutes");

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    // =========================================================================
    // GET /recipes
    // =========================================================================

    @Operation(summary = "List recipes",
               description = "Returns a page of recipes matching the given filter, sort, and pagination "
                       + "parameters. `q` and `tags` are combined with AND semantics when both are present.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Success",
                     content = @Content(schema = @Schema(implementation = RecipePageResponse.class))),
        @ApiResponse(responseCode = "400", description = "Unknown `sort` field",
                     content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping
    public RecipePageResponse listAll(
            @Parameter(description = "Case-insensitive substring match against title or description")
            @RequestParam(required = false) String q,
            @Parameter(description = "Comma-separated tag list; matches recipes having any of the given tags",
                       style = ParameterStyle.FORM, explode = Explode.FALSE)
            @RequestParam(required = false) List<String> tags,
            @Parameter(description = "Sort field and direction as `field,dir`. Allowed fields: title, "
                    + "prepTimeMinutes, cookTimeMinutes, createdAt, updatedAt. prepTimeMinutes/cookTimeMinutes "
                    + "always sort null values last.",
                       example = "title,asc")
            @RequestParam(defaultValue = "title,asc") String sort,
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size; clamped to a maximum of 100", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = buildPageable(sort, page, size);
        return service.findAll(q, tags, pageable);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a {@link Pageable} from the raw {@code sort}/{@code page}/{@code size}
     * query params: parses {@code sort} into a {@link Sort.Order} (nulls-last for
     * prepTimeMinutes/cookTimeMinutes), rejects unknown sort fields with a 400,
     * clamps {@code size} to {@link #MAX_PAGE_SIZE} instead of erroring, and
     * guards against negative/zero page or size values.
     */
    private Pageable buildPageable(String sort, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize, parseSortOrder(sort));
    }

    private Sort parseSortOrder(String sort) {
        String[] parts = (sort == null ? "" : sort).split(",", 2);
        String field = parts[0].trim();
        if (field.isEmpty()) {
            field = "title";
        }
        if (!ALLOWED_SORT_FIELDS.contains(field)) {
            throw new InvalidSortFieldException(field);
        }

        String rawDirection = parts.length > 1 ? parts[1].trim() : "asc";
        Sort.Direction direction = Sort.Direction.fromOptionalString(rawDirection).orElse(Sort.Direction.ASC);

        Sort.Order order = Sort.Order.by(field).with(direction);
        if (NULLS_LAST_SORT_FIELDS.contains(field)) {
            order = order.nullsLast();
        }
        return Sort.by(order);
    }

    // =========================================================================
    // GET /recipes/{id}
    // =========================================================================

    @Operation(summary = "Get a single recipe",
               description = "Returns the recipe with the given id.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Recipe found",
                     content = @Content(schema = @Schema(implementation = RecipeResponse.class))),
        @ApiResponse(responseCode = "404", description = "Recipe not found",
                     content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{id}")
    public RecipeResponse getById(
            @Parameter(description = "Recipe surrogate key", example = "1") @PathVariable Long id) {
        return service.findById(id);
    }

    // =========================================================================
    // POST /recipes
    // =========================================================================

    @Operation(summary = "Create a recipe",
               description = "Persists a new recipe and returns the created resource, including generated id and timestamps.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Recipe created",
                     content = @Content(schema = @Schema(implementation = RecipeResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation failed",
                     content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public RecipeResponse create(
            @Valid @RequestBody RecipeRequest request, @AuthenticationPrincipal UserPrincipal principal) {
        return service.create(request, principal.getId());
    }

    // =========================================================================
    // PUT /recipes/{id}
    // =========================================================================

    @Operation(summary = "Update a recipe",
               description = "Replaces all mutable fields (title, description, content) on an existing recipe.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Recipe updated",
                     content = @Content(schema = @Schema(implementation = RecipeResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation failed",
                     content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "404", description = "Recipe not found",
                     content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public RecipeResponse update(
            @Parameter(description = "Recipe surrogate key", example = "1") @PathVariable Long id,
            @Valid @RequestBody RecipeRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return service.update(id, request, principal.getId());
    }

    // =========================================================================
    // DELETE /recipes/{id}
    // =========================================================================

    @Operation(summary = "Delete a recipe",
               description = "Removes the recipe permanently. Returns 204 on success or 404 when the id does not exist.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Recipe deleted"),
        @ApiResponse(responseCode = "404", description = "Recipe not found",
                     content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @Parameter(description = "Recipe surrogate key", example = "1") @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        service.delete(id, principal.getId());
    }

    // =========================================================================
    // POST /recipes/{id}/image
    // =========================================================================

    @Operation(summary = "Upload a recipe's hero image",
               description = "Uploads (or replaces) the recipe's single hero image. Accepts "
                       + "multipart/form-data with a single part named `file`. Only image/jpeg, "
                       + "image/png, and image/webp are accepted, and the bytes must actually "
                       + "decode as an image regardless of the declared content type. Replacing "
                       + "an existing image deletes the previous file from disk.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Image stored; updated recipe returned",
                     content = @Content(schema = @Schema(implementation = RecipeResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid image (bad/unsupported content "
                + "type, file does not decode as an image, or exceeds the size limit)",
                     content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "404", description = "Recipe not found",
                     content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RecipeResponse uploadImage(
            @Parameter(description = "Recipe surrogate key", example = "1") @PathVariable Long id,
            @Parameter(description = "Image file (image/jpeg, image/png, or image/webp), max 5MB")
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        return service.uploadImage(id, file, principal.getId());
    }

    // =========================================================================
    // DELETE /recipes/{id}/image
    // =========================================================================

    @Operation(summary = "Delete a recipe's hero image",
               description = "Removes the stored image file and clears the recipe's image "
                       + "reference. Idempotent: returns 200 with the unchanged recipe when it "
                       + "already had no image, rather than erroring.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Image removed (or recipe already had none); updated recipe returned",
                     content = @Content(schema = @Schema(implementation = RecipeResponse.class))),
        @ApiResponse(responseCode = "404", description = "Recipe not found",
                     content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @DeleteMapping("/{id}/image")
    public RecipeResponse deleteImage(
            @Parameter(description = "Recipe surrogate key", example = "1") @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return service.deleteImage(id, principal.getId());
    }

    // =========================================================================
    // GET /recipes/{id}/image
    // =========================================================================

    @Operation(summary = "Get a recipe's hero image",
               description = "Streams the raw image bytes for the recipe's hero image. Filenames "
                       + "are content-addressed, so responses are cacheable indefinitely.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Image bytes",
                     content = @Content(mediaType = "image/*")),
        @ApiResponse(responseCode = "404", description = "Recipe not found, or recipe has no image",
                     content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping(value = "/{id}/image",
                produces = {MediaType.IMAGE_JPEG_VALUE, MediaType.IMAGE_PNG_VALUE, "image/webp"})
    public ResponseEntity<Resource> getImage(
            @Parameter(description = "Recipe surrogate key", example = "1") @PathVariable Long id) {
        RecipeImage image = service.loadImage(id);
        return ResponseEntity.ok()
                .contentType(image.contentType())
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).immutable())
                .eTag(image.eTag())
                .body(image.resource());
    }
}
