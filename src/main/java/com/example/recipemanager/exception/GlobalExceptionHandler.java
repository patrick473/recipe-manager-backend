package com.example.recipemanager.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralised error handler that maps domain exceptions to RFC 7807 Problem Details.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RecipeNotFoundException.class)
    public ProblemDetail handleNotFound(RecipeNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("https://example.com/errors/recipe-not-found"));
        pd.setTitle("Recipe Not Found");
        return pd;
    }

    @ExceptionHandler(InvalidSortFieldException.class)
    public ProblemDetail handleInvalidSortField(InvalidSortFieldException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setType(URI.create("https://example.com/errors/invalid-sort-field"));
        pd.setTitle("Invalid Sort Field");
        return pd;
    }

    @ExceptionHandler(InvalidImageException.class)
    public ProblemDetail handleInvalidImage(InvalidImageException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setType(URI.create("https://example.com/errors/invalid-image"));
        pd.setTitle("Invalid Image");
        return pd;
    }

    @ExceptionHandler(ImageNotFoundException.class)
    public ProblemDetail handleImageNotFound(ImageNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("https://example.com/errors/image-not-found"));
        pd.setTitle("Image Not Found");
        return pd;
    }

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    public ProblemDetail handleUsernameAlreadyExists(UsernameAlreadyExistsException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI.create("https://example.com/errors/username-taken"));
        pd.setTitle("Username Already Taken");
        return pd;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Uploaded file exceeds the maximum allowed size");
        pd.setType(URI.create("https://example.com/errors/image-too-large"));
        pd.setTitle("Image Too Large");
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage, (a, b) -> a));

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request validation failed");
        pd.setType(URI.create("https://example.com/errors/validation-failed"));
        pd.setTitle("Validation Failed");
        pd.setProperty("errors", fieldErrors);
        return pd;
    }

    /**
     * Catches the DB-level unique constraint violation that surfaces when two
     * {@code POST /auth/register} requests for the same username both pass the
     * app-level {@code existsByUsername} check before either has committed —
     * the {@code users.username} unique constraint is what actually catches
     * that race, not {@link UsernameAlreadyExistsException}. {@code username}
     * is the only unique constraint in the schema, so this maps to the same
     * 409 response as the non-race case.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Username already taken");
        pd.setType(URI.create("https://example.com/errors/username-taken"));
        pd.setTitle("Username Already Taken");
        return pd;
    }

    /**
     * A no-op handler that exists only to keep {@link #handleUnexpected} from
     * swallowing Spring Security's exceptions. {@code AuthenticationException}
     * (e.g. the {@code BadCredentialsException} thrown by
     * {@code authenticationManager.authenticate()} in {@code AuthController})
     * and {@code AccessDeniedException} are ordinarily never resolved here at
     * all — they're meant to propagate out of the {@code DispatcherServlet} so
     * Spring Security's {@code ExceptionTranslationFilter}, which wraps the
     * whole filter chain, can catch them and delegate to
     * {@code ProblemDetailAuthenticationEntryPoint}/
     * {@code ProblemDetailAccessDeniedHandler} for the 401/403 response. Once a
     * catch-all {@code Exception.class} handler exists, though, Spring MVC's
     * exception resolution would otherwise match these subtypes too and
     * resolve them into a 500 before they ever reach that filter. Rethrowing
     * the same exception instance here makes
     * {@code ExceptionHandlerExceptionResolver} treat it as unhandled, so it
     * keeps propagating exactly as if this class didn't exist.
     */
    @ExceptionHandler({AuthenticationException.class, AccessDeniedException.class})
    public void rethrowSecurityException(RuntimeException ex) {
        throw ex;
    }

    /**
     * Last-resort handler for anything not already mapped above. Deliberately
     * omits {@code ex.getMessage()} and the stack trace from the response body
     * so an unanticipated failure never leaks internal details to the client.
     * Declared last for readability only — {@code @ExceptionHandler} resolution
     * always picks the most specific matching handler regardless of declaration
     * order, so this only ever fires when nothing more specific matches.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        pd.setType(URI.create("https://example.com/errors/internal-error"));
        pd.setTitle("Internal Server Error");
        return pd;
    }
}
