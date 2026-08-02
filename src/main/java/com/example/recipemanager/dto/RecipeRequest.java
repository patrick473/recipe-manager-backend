package com.example.recipemanager.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request body for creating or updating a recipe.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "RecipeRequest", description = "Payload for creating or replacing a recipe")
public class RecipeRequest {

    @NotBlank(message = "Title must not be blank")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    @Schema(description = "Short human-readable title", example = "Classic Banana Bread", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Size(max = 512, message = "Description must not exceed 512 characters")
    @Schema(description = "Optional one-line summary", example = "Moist and simple banana bread, ready in 1 hour")
    private String description;

    @NotBlank(message = "Content must not be blank")
    @Size(max = 50000, message = "Content must not exceed 50000 characters")
    @Schema(description = "Full recipe body in Markdown", example = "## Ingredients\n- 3 ripe bananas\n\n## Steps\n1. Mash bananas.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    @Size(max = 20, message = "Tags must not exceed 20 items")
    @Schema(description = "Optional freeform labels for categorizing and filtering recipes", example = "[\"breakfast\", \"quick\"]")
    private List<@NotBlank(message = "Tag must not be blank") @Size(max = 50, message = "Tag must not exceed 50 characters") String> tags;

    @Min(value = 0, message = "Prep time must not be negative")
    @Schema(description = "Estimated preparation time in minutes", example = "10")
    private Integer prepTimeMinutes;

    @Min(value = 0, message = "Cook time must not be negative")
    @Schema(description = "Estimated cooking time in minutes", example = "60")
    private Integer cookTimeMinutes;

    @Min(value = 0, message = "Servings must not be negative")
    @Schema(description = "Number of servings this recipe yields", example = "8")
    private Integer servings;
}
