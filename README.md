# Recipe Manager — Backend

Spring Boot REST API for the Recipe Manager application. Exposes JWT-authenticated CRUD endpoints for Markdown-based recipes — with pagination, search/filtering, sorting, and hero-image upload — plus account registration/login, backed by an in-memory H2 database in development and PostgreSQL in production.

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

Requirements: Java 25+, Maven 3.9+. No Maven wrapper is checked in, so use the system `mvn`.

```bash
# Clone and run
git clone https://github.com/patrick473/recipe-manager-backend.git
cd recipe-manager-backend

# Run in development mode (H2 in-memory database)
mvn spring-boot:run

# The API is available at:
#   http://localhost:8080/recipes
# (GET /recipes, GET /recipes/{id}, and GET /recipes/{id}/image are public;
#  everything else requires a JWT — see "API endpoints" below)
#
# Swagger UI is available at:
#   http://localhost:8080/swagger-ui.html
#
# OpenAPI JSON is available at:
#   http://localhost:8080/api-docs
#
# H2 console (disabled by default — see below to re-enable locally):
#   http://localhost:8080/h2-console
#   JDBC URL: jdbc:h2:mem:recipedb
```

The H2 console is disabled by default (`spring.h2.console.enabled=false`) since it's an
unauthenticated database UI. To use it locally for debugging, temporarily flip that
property to `true` in `src/main/resources/application.properties`, restart the app, and
remember to set it back before committing.

---

## API endpoints

All recipe *mutations* (create/update/delete a recipe, upload/delete its image) require a
JWT in an `Authorization: Bearer <token>` header, obtained from `POST /auth/register` or
`POST /auth/login`. Reads (`GET /recipes`, `GET /recipes/{id}`, `GET /recipes/{id}/image`)
are public and need no token. A mutation targeting another user's recipe returns a plain
`404` (never `403`) — existence of another account's recipe is never revealed to a
non-owner.

| Method | Path | Auth | Status | Description |
|--------|------|------|--------|-------------|
| POST | `/auth/register` | — | 201 / 400 / 409 | Register a new account (also logs it in) |
| POST | `/auth/login` | — | 200 / 401 | Log into an existing account |
| GET | `/recipes` | — | 200 / 400 | List recipes (paginated, filterable, sortable) |
| GET | `/recipes/{id}` | — | 200 / 404 | Get a single recipe |
| POST | `/recipes` | Bearer | 201 / 400 / 401 | Create a new recipe |
| PUT | `/recipes/{id}` | Bearer | 200 / 400 / 401 / 404 | Update an existing recipe |
| DELETE | `/recipes/{id}` | Bearer | 204 / 401 / 404 | Delete a recipe |
| POST | `/recipes/{id}/image` | Bearer | 200 / 400 / 401 / 404 | Upload or replace a recipe's hero image |
| DELETE | `/recipes/{id}/image` | Bearer | 200 / 401 / 404 | Remove a recipe's hero image |
| GET | `/recipes/{id}/image` | — | 200 / 404 | Get a recipe's hero image bytes |

### POST /auth/register

```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username": "jsmith", "password": "correcthorsebattery"}'
```

Returns `201 Created` with a token in the same shape as `POST /auth/login` — no separate
login call is needed afterward. `username` must not be blank; `password` must be 8-72
characters. Returns `409 Conflict` if the username is already taken (including the race
where two concurrent registrations for the same username both pass validation).

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "userId": 1,
  "username": "jsmith"
}
```

### POST /auth/login

```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "jsmith", "password": "correcthorsebattery"}'
```

Returns `200 OK` with the same `AuthResponse` shape as register. Returns `401 Unauthorized`
for a wrong username *or* wrong password — the response never reveals which one was
incorrect. Tokens expire after 24 hours (`app.jwt.expiration-ms`, default `86400000`).

### GET /recipes

Returns a page of recipes, optionally filtered and sorted.

| Query param | Default | Description |
|-------------|---------|-------------|
| `q` | — | Case-insensitive substring match against `title` OR `description` |
| `tags` | — | Comma-separated tags; matches recipes with *any* of the given tags (OR among tags, AND with `q` if both present) |
| `sort` | `title,asc` | `field,dir`. Allowed fields: `title`, `prepTimeMinutes`, `cookTimeMinutes`, `createdAt`, `updatedAt`. `prepTimeMinutes`/`cookTimeMinutes` always sort `null` last, regardless of direction |
| `page` | `0` | Zero-based page index |
| `size` | `20` | Page size, clamped (not rejected) to a max of `100` |

```bash
curl "http://localhost:8080/recipes?q=banana&tags=breakfast,quick&sort=title,asc&page=0&size=20"
```

```json
{
  "content": [
    {
      "id": 1,
      "title": "Classic Banana Bread",
      "description": "Moist and simple banana bread, ready in 1 hour",
      "content": "## Ingredients\n- 3 ripe bananas\n\n## Steps\n1. Mash bananas.",
      "tags": ["breakfast", "quick"],
      "prepTimeMinutes": 10,
      "cookTimeMinutes": 60,
      "servings": 8,
      "imageUrl": null,
      "createdAt": "2024-01-15T09:00:00Z",
      "updatedAt": "2024-01-15T09:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

Returns `400 Bad Request` if `sort` references a field outside the allowed list.

### GET /recipes/{id}

```bash
curl http://localhost:8080/recipes/1
```

Returns a single `RecipeResponse` (see shape above). Returns `404` with a Problem Detail
body if the id does not exist.

### POST /recipes

```bash
curl -X POST http://localhost:8080/recipes \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Classic Banana Bread",
    "description": "Moist and simple banana bread, ready in 1 hour",
    "content": "## Ingredients\n- 3 ripe bananas\n\n## Steps\n1. Mash bananas.",
    "tags": ["breakfast", "quick"],
    "prepTimeMinutes": 10,
    "cookTimeMinutes": 60,
    "servings": 8
  }'
```

Returns `201 Created` with the full resource including generated `id`, `imageUrl: null`,
and timestamps. Returns `400 Bad Request` with field-level validation errors if `title` or
`content` is missing/invalid. Returns `401 Unauthorized` without a valid bearer token.

### PUT /recipes/{id}

```bash
curl -X PUT http://localhost:8080/recipes/1 \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Updated Banana Bread",
    "content": "## Ingredients\n- 4 ripe bananas",
    "prepTimeMinutes": 15
  }'
```

Replaces all mutable fields (`title`, `description`, `content`, `tags`, `prepTimeMinutes`,
`cookTimeMinutes`, `servings`). `id`, `imageUrl`, `createdAt`, and `updatedAt` are
server-managed and cannot be set by the caller. Returns `200 OK` with the updated resource.
Returns `404` if the recipe doesn't exist *or* isn't owned by the caller.

### DELETE /recipes/{id}

```bash
curl -X DELETE http://localhost:8080/recipes/1 \
  -H "Authorization: Bearer $TOKEN"
```

Returns `204 No Content` on success. Returns `404` if the recipe doesn't exist or isn't
owned by the caller.

### POST /recipes/{id}/image

```bash
curl -X POST http://localhost:8080/recipes/1/image \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@banana-bread.jpg"
```

Uploads (or replaces) the recipe's single hero image. Accepts `image/jpeg`, `image/png`,
or `image/webp` as a multipart part named `file`, up to 5MB; content is sniffed
server-side (`ImageIO.read`) and rejected with `400` if it doesn't actually decode as an
image of the declared type, or if it exceeds the size limit. Replacing an existing image
deletes the previously stored file. Returns `200 OK` with the updated `RecipeResponse` —
`imageUrl` becomes `/recipes/{id}/image?v=<generated-filename>`, a cache-busting query
param that changes automatically on every replace. Returns `404` if the recipe doesn't
exist or isn't owned by the caller.

### DELETE /recipes/{id}/image

```bash
curl -X DELETE http://localhost:8080/recipes/1/image \
  -H "Authorization: Bearer $TOKEN"
```

Removes the stored image file and clears `imageUrl` back to `null`. Idempotent — returns
`200 OK` with the (unchanged) recipe even if it already had no image, rather than
erroring. Returns `404` if the recipe doesn't exist or isn't owned by the caller.

### GET /recipes/{id}/image

```bash
curl http://localhost:8080/recipes/1/image?v=abc123 -o banana-bread.jpg
```

Streams the raw image bytes with a one-year, immutable `Cache-Control` header (safe
because filenames are content-addressed — a new upload gets a new filename/`?v=` value,
so no explicit cache invalidation is needed). Returns `404` if the recipe doesn't exist or
has no image.

---

## Request and response schemas

### RecipeRequest (POST and PUT `/recipes` body)

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `title` | string | yes | max 255, not blank | Short human-readable title |
| `description` | string or null | no | max 512 | Optional one-line summary |
| `content` | string | yes | max 50000, not blank | Full recipe body in Markdown |
| `tags` | string[] or null | no | max 20 items; each tag not blank, max 50 chars | Freeform labels for categorizing/filtering |
| `prepTimeMinutes` | integer or null | no | >= 0 | Estimated preparation time in minutes |
| `cookTimeMinutes` | integer or null | no | >= 0 | Estimated cooking time in minutes |
| `servings` | integer or null | no | >= 0 | Number of servings this recipe yields |

### RecipeResponse

| Field | Type | Description |
|-------|------|-------------|
| `id` | integer | Auto-generated surrogate key |
| `title` | string | Recipe title |
| `description` | string or null | Optional summary |
| `content` | string | Markdown body |
| `tags` | string[] or null | Freeform labels |
| `prepTimeMinutes` | integer or null | Estimated preparation time in minutes |
| `cookTimeMinutes` | integer or null | Estimated cooking time in minutes |
| `servings` | integer or null | Number of servings |
| `imageUrl` | string or null | `/recipes/{id}/image?v=<filename>`, or `null` if no image has been uploaded |
| `createdAt` | ISO-8601 datetime | UTC creation timestamp |
| `updatedAt` | ISO-8601 datetime | UTC last-modified timestamp |

### RecipePageResponse (GET /recipes body)

| Field | Type | Description |
|-------|------|-------------|
| `content` | RecipeResponse[] | The recipes on this page |
| `page` | integer | Zero-based index of this page |
| `size` | integer | Maximum recipes per page |
| `totalElements` | integer | Total recipes matching the filter, across all pages |
| `totalPages` | integer | Total number of pages available |

### RegisterRequest (POST /auth/register body)

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `username` | string | yes | not blank | Unique account username |
| `password` | string | yes | 8-72 chars, not blank | Account password |

### LoginRequest (POST /auth/login body)

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `username` | string | yes | not blank | Account username |
| `password` | string | yes | not blank | Account password |

### AuthResponse (register/login response)

| Field | Type | Description |
|-------|------|-------------|
| `token` | string | Signed JWT — send as `Authorization: Bearer <token>` on subsequent requests |
| `userId` | integer | Authenticated account id |
| `username` | string | Authenticated account username |

---

## Error handling

All errors follow [RFC 7807](https://www.rfc-editor.org/rfc/rfc7807) Problem Details
(`type`, `title`, `status`, `detail`, and — for validation failures only — `errors`).

| Status | `type` suffix | When |
|--------|---------------|------|
| 400 | `validation-failed` | A request body fails `@Valid` constraints (includes field-level `errors` map) |
| 400 | `invalid-sort-field` | `GET /recipes`'s `sort` param references an unknown field |
| 400 | `invalid-image` | Uploaded file has an unsupported content type or doesn't decode as an image |
| 400 | `image-too-large` | Uploaded file exceeds the 5MB limit |
| 401 | `unauthorized` | Missing/invalid/expired bearer token, or wrong login credentials |
| 404 | `recipe-not-found` | No recipe with the given `id` (or it belongs to another user) |
| 404 | `image-not-found` | The recipe exists but has no image |
| 409 | `username-taken` | Username already registered (including a concurrent-registration race) |
| 500 | `internal-error` | Unexpected server error (message body deliberately omits internals) |

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

### 401 Unauthorized

```json
{
  "type": "https://example.com/errors/unauthorized",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Full authentication is required to access this resource"
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

### 409 Username already taken

```json
{
  "type": "https://example.com/errors/username-taken",
  "title": "Username Already Taken",
  "status": 409,
  "detail": "Username already taken: jsmith"
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
| `spring.datasource.password` | `devpassword` | Database password (local H2 dev default; not a real secret) |
| `spring.jpa.hibernate.ddl-auto` | `update` | Schema management strategy |
| `spring.h2.console.enabled` | `false` | Enable H2 console (unauthenticated DB UI — leave off except for local debugging) |
| `app.jwt.secret` | checked-in dev default | HMAC-SHA256 signing key for JWTs. **Must** be overridden (e.g. via `APP_JWT_SECRET`) in any real deployment — anyone holding it can mint tokens for any user |
| `app.jwt.expiration-ms` | `86400000` (24h) | JWT lifetime in milliseconds |
| `app.cors.allowed-origins` | `http://localhost:4200,http://localhost:3000` | Comma-separated list of allowed CORS origins |
| `app.storage.upload-dir` | `uploads` | Filesystem directory where recipe hero images are stored |
| `spring.servlet.multipart.max-file-size` / `max-request-size` | `5MB` | Max recipe image upload size |
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
mvn test
mvn test -Dtest=ClassName                # single test class
mvn test -Dtest=ClassName#methodName     # single test method
```

---

## OpenAPI specification

The hand-authored OpenAPI 3.0.3 spec lives at `openapi.yaml` in the repository root. The same spec is also served live at runtime:

- JSON: `http://localhost:8080/api-docs`
- YAML: `http://localhost:8080/api-docs.yaml`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
