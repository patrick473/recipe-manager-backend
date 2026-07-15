package com.example.recipemanager.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a requested recipe does not exist in the database.
 * Maps to HTTP 404 Not Found via {@link ResponseStatus}.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class RecipeNotFoundException extends RuntimeException {

    public RecipeNotFoundException(Long id) {
        super("Recipe not found: " + id);
    }
}
