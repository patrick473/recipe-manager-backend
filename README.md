# Recipe Manager — Backend

Spring Boot REST API for the Recipe Manager application. Exposes five CRUD endpoints for Markdown-based recipes, backed by an in-memory H2 database in development and PostgreSQL in production.

---

## Table of contents

- [Quick start](#quick-start)
- [API endpoints](#api-endpoints)
- [Request and response schemas](#request-and-response-schemas)
- [Error handling](#error-handling)
- [Configuration](#configuration)
- [Running tests](#running-tests)
- [OpenAPI specification](#openapi-specification)

---

## Quick start

Requirements: Java 17+, Maven 3.9+.

```bash
# Clone and run
git clone https://github.com/patrick473/recipe-manager-backend.git
cd recipe-manager-backend

# Run in development mode (H2 in-memory database)
./mvnw spring-boot:run

# The API is available at:
#   http://localhost:8080/recipes
#
# Swagger UI is available at:
#   http://localhost:8080/swagger-ui.html
#
# OpenAPI JSON is available at:
#   http://localhost:8080/api-docs
#
# H2 console (dev only):
#   http://localhost:8080/h2-console
#   JDBC URL: jdbc:h2:mem:recipedb
```

---

## API endpoints

| Method | Path | Status | Description |
|--------|------|--------|-------------|
| GET | `/recipes` | 200 | List all recipes |
| GET | `/recipes/{id}` | 200 / 404 | Get a single recipe |
| POST | `/recipes` | 201 / 400 | Create a new recipe |
| PUT | `/recipes/{id}` | 200 / 400 / 404 | Update an existing recipe |
| DELETE | `/recipes/{id}` | 204 / 404 | Delete a recipe |

### GET /recipes

Returns every recipe in ascending id order.

```bash
curl http://localhost:8080/recipes
```

```json
[
  {
    "id": 1,
    "title": "Classic Banana Bread",
    "description": "Moist and simple banana bread, ready in 1 hour",
    "content": "## Ingredients\n- 3 ripe bananas\n\n## Steps\n1. Mash bananas.",
    "createdAt": "2024-01-15T09:00:00Z",
    "updatedAt": "2024-01-15T09:00:00Z"
  }
]
```

### GET /recipes/{id}

```bash
curl http://localhost:8080/recipes/1
```

Returns `404` with a Problem Detail body if the id does not exist.

### POST /recipes

```bash
curl -X POST http://localhost:8080/recipes \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Classic Banana Bread",
    "description": "Moist and simple banana bread, ready in 1 hour",
    "content": "## Ingredients\n- 3 ripe bananas\n\n## Steps\n1. Mash bananas."
  }'
```

Returns `201 Created` with the full resource including generated `id` and timestamps.  
Returns `400 Bad Request` with field-level validation errors if `title` or `content` is missing.

### PUT /recipes/{id}

```bash
curl -X PUT http://localhost:8080/recipes/1 \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Updated Banana Bread",
    "content": "## Ingredients\n- 4 ripe bananas"
  }'
```

Replaces all mutable fields. Returns `200 OK` with the updated resource.

### DELETE /recipes/{id}

```bash
curl -X DELETE http://localhost:8080/recipes/1
```

Returns `204 No Content` on success.

---

## Request and response schemas

### RecipeRequest (POST and PUT body)

| Field | Type | Required | Max length | Description |
|-------|------|----------|-----------|-------------|
| `title` | string | yes | 255 | Short human-readable title |
| `description` | string | no | 512 | Optional one-line summary |
| `content` | string | yes | — | Full recipe body in Markdown |

### RecipeResponse

| Field | Type | Description |
|-------|------|-------------|
| `id` | integer | Auto-generated surrogate key |
| `title` | string | Recipe title |
| `description` | string or null | Optional summary |
| `content` | string | Markdown body |
| `createdAt` | ISO-8601 datetime | UTC creation timestamp |
| `updatedAt` | ISO-8601 datetime | UTC last-modified timestamp |

---

## Error handling

All errors follow [RFC 7807](https://www.rfc-editor.org/rfc/rfc7807) Problem Details.

### 400 Validation failed

```json
{
  "type": "https://example.com/errors/validation-failed",
  "title": "Validation Failed",
  "status": 400,
  "detail": "Request validation failed",
  "errors": {
    "title": "Title must not be blank",
    "content": "Content must not be blank"
  }
}
```

### 404 Not found

```json
{
  "type": "https://example.com/errors/recipe-not-found",
  "title": "Recipe Not Found",
  "status": 404,
  "detail": "Recipe not found: 99"
}
```

---

## Configuration

`src/main/resources/application.properties`

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | `8080` | HTTP listen port |
| `spring.datasource.url` | H2 in-memory | JDBC connection URL |
| `spring.datasource.username` | `sa` | Database username |
| `spring.datasource.password` | (empty) | Database password |
| `spring.jpa.hibernate.ddl-auto` | `update` | Schema management strategy |
| `spring.h2.console.enabled` | `true` | Enable H2 console (disable in prod) |
| `springdoc.api-docs.path` | `/api-docs` | OpenAPI JSON endpoint |
| `springdoc.swagger-ui.path` | `/swagger-ui.html` | Swagger UI path |

### Switching to PostgreSQL

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/recipedb
spring.datasource.driver-class-name=org.postgresql.Driver
spring.datasource.username=recipe_user
spring.datasource.password=secret
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.h2.console.enabled=false
```

---

## Running tests

```bash
./mvnw test
```

---

## OpenAPI specification

The hand-authored OpenAPI 3.0.3 spec lives at `openapi.yaml` in the repository root. The same spec is also served live at runtime:

- JSON: `http://localhost:8080/api-docs`
- YAML: `http://localhost:8080/api-docs.yaml`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
