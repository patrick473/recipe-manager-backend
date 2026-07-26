package com.example.recipemanager.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Read model returned by all recipe endpoints.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "RecipeResponse", description = "A recipe returned by the API")
public class RecipeResponse {

    @Schema(description = "Auto-generated surrogate key", example = "1")
    private Long id;

    @Schema(description = "Short human-readable title", example = "Classic Banana Bread")
    private String title;

    @Schema(description = "Optional one-line summary", example = "Moist and simple banana bread, ready in 1 hour")
    private String description;

    @Schema(description = "Full recipe body in Markdown")
    private String content;

    @Schema(description = "Optional freeform labels for categorizing and filtering recipes", example = "[\"breakfast\", \"quick\"]")
    private List<String> tags;

    @Schema(description = "Estimated preparation time in minutes", example = "10")
    private Integer prepTimeMinutes;

    @Schema(description = "Estimated cooking time in minutes", example = "60")
    private Integer cookTimeMinutes;

    @Schema(description = "Number of servings this recipe yields", example = "8")
    private Integer servings;

    @Schema(description = "UTC timestamp of first creation")
    private Instant createdAt;

    @Schema(description = "UTC timestamp of last modification")
    private Instant updatedAt;
}
