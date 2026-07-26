package com.example.recipemanager.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an uploaded recipe image fails validation: an unsupported
 * declared content type, or bytes that do not decode as an image (content
 * sniffing via {@code ImageIO.read} catches a client lying about content type).
 * Maps to HTTP 400 Bad Request via {@link ResponseStatus}.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidImageException extends RuntimeException {

    public InvalidImageException(String message) {
        super(message);
    }
}
