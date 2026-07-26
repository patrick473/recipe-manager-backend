package com.example.recipemanager.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A page of recipes returned by {@code GET /recipes}, wrapping the matching
 * {@link RecipeResponse} objects alongside pagination metadata. Hand-rolled
 * rather than serializing Spring Data's {@code Page<T>} directly, keeping the
 * response contract fully described by {@code openapi.yaml}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "RecipePageResponse", description = "A page of recipes matching the given filter/sort/pagination parameters")
public class RecipePageResponse {

    @Schema(description = "The recipes on this page")
    private List<RecipeResponse> content;

    @Schema(description = "Zero-based index of this page", example = "0")
    private int page;

    @Schema(description = "Maximum number of recipes per page", example = "20")
    private int size;

    @Schema(description = "Total number of recipes matching the filter, across all pages", example = "42")
    private long totalElements;

    @Schema(description = "Total number of pages available", example = "3")
    private int totalPages;
}
