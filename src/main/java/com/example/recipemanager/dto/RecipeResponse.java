package com.example.recipemanager.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

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

    @Schema(description = "UTC timestamp of first creation")
    private Instant createdAt;

    @Schema(description = "UTC timestamp of last modification")
    private Instant updatedAt;
}
