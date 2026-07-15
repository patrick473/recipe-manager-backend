package com.example.recipemanager.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    @Schema(description = "Full recipe body in Markdown", example = "## Ingredients\n- 3 ripe bananas\n\n## Steps\n1. Mash bananas.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;
}
