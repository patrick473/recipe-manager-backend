# Spec: Expand Mock Recipe Data (19 additional recipes)

**Status: implemented.** `DataSeeder` now loads all 20 recipes (the original
"Classic Pancakes" plus 19 new) from a JSON resource file instead of
hardcoded Java.

`DataSeeder.java` used to seed exactly one recipe, hardcoded as a
`Recipe.builder()` call. This spec adds 19 more for variety across meal
types/cuisines so the frontend list/detail/search views have realistic data,
and — per a follow-up ask — moves seed data out of Java entirely so it's
editable without recompiling.

## Key decisions

- **JSON-driven seeding:** data lives in
  `recipe-manager-backend/src/main/resources/data/mock-recipes.json`, shaped
  like `RecipeRequest` (`title`, `description`, `content`, `tags`,
  `prepTimeMinutes`, `cookTimeMinutes`, `servings`). `DataSeeder` injects
  Spring Boot's auto-configured `ObjectMapper`, reads the file via
  `ClassPathResource`, deserializes to `List<RecipeRequest>`, and maps each
  to a `Recipe` entity with the same field mapping `RecipeService.create()`
  uses (a small private `toEntity`, not worth a shared abstraction for two
  call sites). The `if (recipeRepository.count() > 0) return;` guard is
  unchanged.
- **Jackson 3 gotcha:** this project pulls in Jackson 3
  (`spring-boot-starter-jackson`), whose classes live under
  `tools.jackson.*`, not Jackson 2's `com.fasterxml.jackson.*`. The
  auto-configured `ObjectMapper` bean is `tools.jackson.databind.ObjectMapper`
  — importing the Jackson 2 class compiles fine but fails at startup with
  `NoSuchBeanDefinitionException`. Use
  `tools.jackson.databind.ObjectMapper`/`tools.jackson.core.type.TypeReference`
  for any future Jackson code here.

## Data conventions

(matching the original "Classic Pancakes" entry)

- `content`: Markdown with exactly two `##` sections — `## Ingredients`
  (`-` bullets, plain-text quantities) then `## Instructions` (numbered,
  full-sentence steps).
- `tags`: lowercase, hyphenated, 2-4 per recipe.
- `description`: one sentence, <=512 chars (kept under ~100 in practice).
- `title` <=255 chars; `prepTimeMinutes`/`cookTimeMinutes`/`servings`
  non-negative integers (`@Min(0)` on the request DTO — a style convention
  here since the seeder writes entities directly, not an enforced
  constraint).

## Recipes added

Spaghetti Carbonara, Chicken Tikka Masala, Classic Beef Tacos, Margherita
Pizza, Vegetable Stir Fry, Greek Salad, Creamy Tomato Soup, Grilled Cheese
Sandwich, Avocado Toast, Overnight Oats, Banana Bread, Chocolate Chip
Cookies, Classic Beef Burger, Shrimp Scampi, Vegetable Fried Rice, Chicken
Caesar Salad, Homemade Guacamole, Beef Chili, and Lemon Garlic Roasted
Chicken — spanning breakfast/lunch/dinner/dessert/snack across italian,
indian, mexican, asian, mediterranean, and american cuisines, each following
the conventions above. Full ingredient/instruction text lives in
`mock-recipes.json`.

## Testing

`mvn test` passes (only the `contextLoads` smoke test existed; nothing
hardcodes a seeded count). Verified manually via H2 + `GET /recipes`
returning all 20 recipes with expected titles. No frontend changes required
— the generated API client and `RecipeService` are unaffected by seed data.

## Deferred

- Spot-checking Markdown rendering of a few entries via Swagger UI against
  `RecipeDetailComponent`'s `marked` parsing.
- Restarting any already-running dev backend — a stale instance keeps
  serving the old single-recipe seed until restarted.
