package com.example.recipemanager.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GlobalExceptionHandler}: invokes the handler methods
 * directly with constructed exceptions and asserts on the returned
 * {@link ProblemDetail}, rather than standing up a Spring context — the
 * handler methods have no dependencies of their own to wire.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // -------------------------------------------------------------------------
    // DataIntegrityViolationException (concurrent-registration race)
    // -------------------------------------------------------------------------

    @Test
    void handleDataIntegrityViolationReturns409WithUsernameTakenBody() {
        DataIntegrityViolationException ex =
                new DataIntegrityViolationException("could not execute statement; constraint [users.username]");

        ProblemDetail pd = handler.handleDataIntegrityViolation(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(pd.getDetail()).isEqualTo("Username already taken");
        assertThat(pd.getType()).isEqualTo(URI.create("https://example.com/errors/username-taken"));
        assertThat(pd.getTitle()).isEqualTo("Username Already Taken");
    }

    @Test
    void handleDataIntegrityViolationDoesNotLeakExceptionMessage() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException: Unique index or primary key violation "
                        + "at INSERT INTO users(...) VALUES (...)");

        ProblemDetail pd = handler.handleDataIntegrityViolation(ex);

        assertThat(pd.getDetail()).doesNotContain("JdbcSQLIntegrityConstraintViolationException", "INSERT INTO");
    }

    // -------------------------------------------------------------------------
    // Exception (catch-all)
    // -------------------------------------------------------------------------

    @Test
    void handleUnexpectedReturns500WithGenericBody() {
        Exception ex = new IllegalStateException("connection pool exhausted: db-primary-7 unreachable");

        ProblemDetail pd = handler.handleUnexpected(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(pd.getDetail()).isEqualTo("An unexpected error occurred");
        assertThat(pd.getType()).isEqualTo(URI.create("https://example.com/errors/internal-error"));
        assertThat(pd.getTitle()).isEqualTo("Internal Server Error");
    }

    @Test
    void handleUnexpectedDoesNotLeakExceptionMessageOrType() {
        Exception ex = new IllegalStateException("connection pool exhausted: db-primary-7 unreachable");

        ProblemDetail pd = handler.handleUnexpected(ex);

        assertThat(pd.getDetail()).doesNotContain("connection pool", "db-primary-7");
        assertThat(pd.getDetail()).doesNotContain(ex.getClass().getName());
    }
}
