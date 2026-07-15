package com.example.recipemanager.controller;

import com.example.recipemanager.dto.RecipeRequest;
import com.example.recipemanager.dto.RecipeResponse;
import com.example.recipemanager.service.RecipeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    // =========================================================================
    // GET /recipes
    // =========================================================================

    @Operation(summary = "List all recipes",
               description = "Returns every recipe in the database ordered by ascending id.")
    @ApiResponse(responseCode = "200", description = "Success",
                 content = @Content(array = @ArraySchema(schema = @Schema(implementation = RecipeResponse.class))))
    @GetMapping
    public List<RecipeResponse> listAll() {
        return service.findAll();
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
