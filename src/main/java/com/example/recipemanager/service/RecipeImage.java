package com.example.recipemanager.service;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

/**
 * Everything {@code GET /recipes/{id}/image} needs to write its response:
 * the raw bytes plus the headers ({@code Content-Type}, {@code ETag}) that
 * only {@link RecipeService} — not the storage backend — has enough
 * context to determine.
 */
public record RecipeImage(Resource resource, MediaType contentType, String eTag) {
}
