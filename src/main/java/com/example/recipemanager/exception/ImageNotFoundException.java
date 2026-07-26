package com.example.recipemanager.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown by {@code GET /recipes/{id}/image} when there is nothing to stream back —
 * either the recipe itself does not exist, or it exists but has no image. Both
 * cases collapse to the same 404 from this endpoint's point of view.
 * Maps to HTTP 404 Not Found via {@link ResponseStatus}.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ImageNotFoundException extends RuntimeException {

    public ImageNotFoundException(Long recipeId) {
        super("No image for recipe: " + recipeId);
    }
}
