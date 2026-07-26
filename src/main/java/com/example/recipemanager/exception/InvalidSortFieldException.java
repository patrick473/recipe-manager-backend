package com.example.recipemanager.exception;

/**
 * Thrown when the {@code sort} query parameter on {@code GET /recipes}
 * references a field that is not one of the supported sort fields.
 * Maps to HTTP 400 Bad Request via {@link GlobalExceptionHandler}.
 */
public class InvalidSortFieldException extends RuntimeException {

    public InvalidSortFieldException(String field) {
        super("Unknown sort field: " + field);
    }
}
