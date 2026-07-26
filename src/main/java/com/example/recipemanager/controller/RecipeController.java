package com.example.recipemanager.controller;

import com.example.recipemanager.dto.RecipePageResponse;
import com.example.recipemanager.dto.RecipeRequest;
import com.example.recipemanager.dto.RecipeResponse;
import com.example.recipemanager.exception.InvalidSortFieldException;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

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
    public RecipeResponse create(@Valid @RequestBody RecipeRequest request) {
        return service.create(request);
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
            @Valid @RequestBody RecipeRequest request) {
        return service.update(id, request);
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
            @Parameter(description = "Recipe surrogate key", example = "1") @PathVariable Long id) {
        service.delete(id);
    }
}
