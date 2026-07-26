# Spec: Expand Mock Recipe Data (19 additional recipes)

**Status: implemented.** `DataSeeder` now loads recipes from a JSON resource file instead
of hardcoded Java, and that file carries all 20 recipes (the original "Classic Pancakes"
plus the 19 below).

## Goal

`DataSeeder.java` used to seed exactly one recipe ("Classic Pancakes"), hardcoded as a
`Recipe.builder()` call. This spec adds 19 more for variety across meal types and cuisines
so the frontend list/detail/search views have realistic data to exercise, and — per a
follow-up ask — moves the seed data out of Java code entirely so it can be edited without
recompiling.

## Architecture: JSON-driven seeding

- Seed data lives in `recipe-manager-backend/src/main/resources/data/mock-recipes.json`, a
  JSON array where each element has the same shape as `RecipeRequest` (`title`,
  `description`, `content`, `tags`, `prepTimeMinutes`, `cookTimeMinutes`, `servings`).
- `DataSeeder` no longer builds `Recipe` entities inline. It injects Spring Boot's
  auto-configured `ObjectMapper`, reads `data/mock-recipes.json` off the classpath via
  `ClassPathResource`, deserializes it to `List<RecipeRequest>`, and maps each one to a
  `Recipe` entity with the same field-by-field mapping `RecipeService.create()` uses (kept
  as a small private `toEntity` — not worth a shared abstraction for two call sites).
- The existing guard is unchanged: `if (recipeRepository.count() > 0) return;`, so this is
  still a no-op against a database that already has data.
- **Gotcha (Spring Boot 4.1 / Jackson 3):** this project pulls in Jackson 3 via
  `spring-boot-starter-jackson`, whose classes live under the `tools.jackson.*` package
  root, not the classic Jackson 2 `com.fasterxml.jackson.*`. The auto-configured
  `ObjectMapper` bean is `tools.jackson.databind.ObjectMapper` — importing the Jackson 2
  class instead compiles fine but fails at startup with
  `NoSuchBeanDefinitionException: No qualifying bean of type
  'com.fasterxml.jackson.databind.ObjectMapper'`. Use `tools.jackson.databind.ObjectMapper`
  and `tools.jackson.core.type.TypeReference` for any future Jackson code in this repo.

## Conventions the JSON data follows (matching the original "Classic Pancakes" entry)

- Build each recipe with `Recipe.builder()...build()`, added to the same list passed to
  `recipeRepository.saveAll(...)` (or repeated `.save(...)` calls, matching whatever pattern
  the file currently uses).
- `content` is a Markdown text block (`"""..."""`) with exactly two `##` sections, in order:
  `## Ingredients` (a `-` bullet list, quantities as plain text, e.g. `1 1/2 cups flour`) and
  `## Instructions` (a numbered list of full-sentence steps).
- `tags` are lowercase, freeform, no spaces (use hyphens, e.g. `"comfort-food"`), 2-4 tags per
  recipe.
- `description` is a single sentence, <=512 chars (in practice keep it under ~100 chars to
  match the existing style).
- `title` <=255 chars — all entries below are short, so no risk there.
- `prepTimeMinutes`, `cookTimeMinutes`, `servings` are all non-negative integers (`@Min(0)`
  on the request DTO; the seeder writes entities directly so this is a style convention, not
  an enforced constraint).

## The 19 recipes to add

### 1. Spaghetti Carbonara
- description: Classic Roman pasta with eggs, pecorino, and crispy pancetta.
- tags: dinner, italian, pasta
- prepTimeMinutes: 10, cookTimeMinutes: 15, servings: 4
- content:
  - Ingredients: 400g spaghetti; 150g pancetta, diced; 3 large eggs; 1 cup grated pecorino romano; 1 tsp black pepper; salt to taste
  - Instructions: Bring a large pot of salted water to boil and cook spaghetti until al dente. While pasta cooks, fry pancetta in a skillet until crisp. Whisk eggs and pecorino together in a bowl. Drain pasta, reserving 1 cup of pasta water, and add pasta to the skillet with pancetta off heat. Quickly stir in the egg mixture, adding pasta water a splash at a time until creamy. Season with black pepper and serve immediately.

### 2. Chicken Tikka Masala
- description: Marinated grilled chicken simmered in a spiced tomato-cream sauce.
- tags: dinner, indian, spicy
- prepTimeMinutes: 30, cookTimeMinutes: 30, servings: 4
- content:
  - Ingredients: 700g chicken thighs, cubed; 1 cup yogurt; 2 tbsp tikka masala spice blend; 1 onion, diced; 3 cloves garlic, minced; 1 tbsp ginger, minced; 400g crushed tomatoes; 1 cup heavy cream; 2 tbsp butter; salt to taste
  - Instructions: Marinate chicken in yogurt and half the spice blend for at least 30 minutes. Grill or pan-sear chicken until charred and cooked through, then set aside. In the same pan, melt butter and sauté onion, garlic, and ginger until soft. Stir in remaining spice blend and crushed tomatoes, and simmer for 10 minutes. Stir in cream and cooked chicken, and simmer until heated through. Serve over rice with naan.

### 3. Classic Beef Tacos
- description: Seasoned ground beef tacos with fresh toppings.
- tags: dinner, mexican, quick
- prepTimeMinutes: 10, cookTimeMinutes: 15, servings: 4
- content:
  - Ingredients: 500g ground beef; 1 packet taco seasoning (or 2 tbsp chili powder blend); 8 small corn tortillas; 1 cup shredded lettuce; 1 cup diced tomato; 1 cup shredded cheddar; sour cream to taste
  - Instructions: Brown ground beef in a skillet over medium heat, breaking it up as it cooks. Drain excess fat, then stir in taco seasoning and a splash of water, simmering for 5 minutes. Warm tortillas in a dry skillet or microwave. Fill each tortilla with beef, lettuce, tomato, and cheddar. Top with sour cream and serve.

### 4. Margherita Pizza
- description: Simple Neapolitan-style pizza with tomato, mozzarella, and basil.
- tags: dinner, italian, vegetarian
- prepTimeMinutes: 20, cookTimeMinutes: 12, servings: 2
- content:
  - Ingredients: 1 pizza dough ball; 1/2 cup crushed San Marzano tomatoes; 200g fresh mozzarella, torn; fresh basil leaves; 2 tbsp olive oil; salt to taste
  - Instructions: Preheat oven to its highest setting with a pizza stone or steel inside, if available. Stretch the dough into a round on a floured surface. Spread crushed tomatoes evenly, leaving a border for the crust. Top with torn mozzarella and a drizzle of olive oil. Bake until the crust is golden and cheese is bubbling, 10-12 minutes. Top with fresh basil and a pinch of salt before serving.

### 5. Vegetable Stir Fry
- description: Quick high-heat stir fry with crisp vegetables in a savory sauce.
- tags: dinner, vegan, asian, quick
- prepTimeMinutes: 15, cookTimeMinutes: 10, servings: 3
- content:
  - Ingredients: 2 cups broccoli florets; 1 red bell pepper, sliced; 1 carrot, julienned; 1 cup snap peas; 3 tbsp soy sauce; 1 tbsp sesame oil; 2 cloves garlic, minced; 1 tbsp cornstarch; 2 tbsp vegetable oil
  - Instructions: Whisk soy sauce, sesame oil, and cornstarch together in a small bowl to make the sauce. Heat vegetable oil in a wok over high heat until shimmering. Add garlic and stir-fry for 30 seconds until fragrant. Add vegetables and stir-fry for 4-5 minutes until crisp-tender. Pour in the sauce and toss until it thickens and coats the vegetables. Serve immediately over rice or noodles.

### 6. Greek Salad
- description: Crisp cucumber and tomato salad with feta, olives, and oregano.
- tags: lunch, vegetarian, mediterranean, no-cook
- prepTimeMinutes: 15, cookTimeMinutes: 0, servings: 4
- content:
  - Ingredients: 4 tomatoes, cut into wedges; 1 cucumber, sliced; 1 red onion, thinly sliced; 200g feta, cubed; 1/2 cup kalamata olives; 3 tbsp olive oil; 1 tbsp red wine vinegar; 1 tsp dried oregano
  - Instructions: Combine tomatoes, cucumber, and red onion in a large bowl. Add feta and olives on top. Whisk olive oil, red wine vinegar, and oregano together in a small bowl. Drizzle dressing over the salad just before serving.

### 7. Creamy Tomato Soup
- description: Smooth roasted tomato soup finished with cream.
- tags: lunch, soup, vegetarian
- prepTimeMinutes: 10, cookTimeMinutes: 35, servings: 4
- content:
  - Ingredients: 1kg ripe tomatoes, halved; 1 onion, quartered; 3 cloves garlic; 3 tbsp olive oil; 2 cups vegetable stock; 1/2 cup heavy cream; salt and pepper to taste
  - Instructions: Preheat oven to 200C (400F). Toss tomatoes, onion, and garlic with olive oil and roast for 30 minutes until caramelized. Transfer roasted vegetables to a pot with vegetable stock and bring to a simmer. Blend the mixture until smooth using an immersion blender. Stir in the cream and season with salt and pepper. Serve hot with crusty bread.

### 8. Grilled Cheese Sandwich
- description: Buttery pan-grilled sandwich with melted cheese.
- tags: lunch, quick, comfort-food
- prepTimeMinutes: 5, cookTimeMinutes: 8, servings: 1
- content:
  - Ingredients: 2 slices sandwich bread; 2 slices cheddar cheese; 1 tbsp butter, softened
  - Instructions: Butter one side of each bread slice. Place one slice butter-side down in a skillet over medium-low heat. Layer cheese on top, then close with the second slice, butter-side up. Cook until golden brown, then flip and cook the other side until the cheese is fully melted. Slice in half and serve warm.

### 9. Avocado Toast
- description: Toasted bread topped with mashed avocado, lemon, and chili flakes.
- tags: breakfast, vegan, quick
- prepTimeMinutes: 5, cookTimeMinutes: 3, servings: 1
- content:
  - Ingredients: 2 slices sourdough bread; 1 ripe avocado; 1/2 lemon, juiced; 1/4 tsp chili flakes; salt to taste
  - Instructions: Toast the bread slices until golden and crisp. Mash the avocado with lemon juice and a pinch of salt in a bowl. Spread the mashed avocado evenly over the toast. Sprinkle with chili flakes and serve immediately.

### 10. Overnight Oats
- description: No-cook make-ahead oats soaked in milk with fruit and honey.
- tags: breakfast, vegan, make-ahead
- prepTimeMinutes: 5, cookTimeMinutes: 0, servings: 1
- content:
  - Ingredients: 1/2 cup rolled oats; 1/2 cup milk (or plant milk); 1 tbsp chia seeds; 1 tbsp honey or maple syrup; 1/2 cup mixed berries
  - Instructions: Combine oats, milk, chia seeds, and honey in a jar or container. Stir well to make sure the oats are fully submerged. Cover and refrigerate overnight, or at least 4 hours. Top with fresh berries before serving.

### 11. Banana Bread
- description: Moist banana bread loaf spiced with cinnamon.
- tags: dessert, baking, breakfast
- prepTimeMinutes: 15, cookTimeMinutes: 60, servings: 8
- content:
  - Ingredients: 3 ripe bananas, mashed; 1/3 cup melted butter; 3/4 cup sugar; 1 egg; 1 tsp vanilla extract; 1 tsp baking soda; pinch of salt; 1 tsp cinnamon; 1 1/2 cups all-purpose flour
  - Instructions: Preheat oven to 175C (350F) and grease a loaf pan. Mix mashed bananas with melted butter in a large bowl. Stir in sugar, egg, and vanilla extract. Sprinkle baking soda, salt, and cinnamon over the mixture and stir in. Fold in the flour until just combined, then pour batter into the loaf pan. Bake for 55-60 minutes, until a toothpick inserted in the center comes out clean.

### 12. Chocolate Chip Cookies
- description: Chewy classic cookies loaded with chocolate chips.
- tags: dessert, baking
- prepTimeMinutes: 15, cookTimeMinutes: 12, servings: 24
- content:
  - Ingredients: 1 cup butter, softened; 3/4 cup sugar; 3/4 cup brown sugar; 2 eggs; 1 tsp vanilla extract; 2 1/4 cups all-purpose flour; 1 tsp baking soda; 1 tsp salt; 2 cups chocolate chips
  - Instructions: Preheat oven to 190C (375F) and line baking sheets with parchment paper. Cream butter with sugar and brown sugar until light and fluffy. Beat in eggs and vanilla extract. Mix in flour, baking soda, and salt until just combined, then fold in chocolate chips. Drop rounded tablespoons of dough onto the baking sheets, spaced apart. Bake for 9-11 minutes until edges are golden, then cool on the sheet for a few minutes before transferring to a rack.

### 13. Classic Beef Burger
- description: Juicy grilled beef patty with classic burger toppings.
- tags: dinner, quick, grill
- prepTimeMinutes: 10, cookTimeMinutes: 10, servings: 4
- content:
  - Ingredients: 600g ground beef (80/20); 4 burger buns; 4 slices cheddar cheese; lettuce, tomato, and onion slices; salt and pepper to taste; condiments to taste
  - Instructions: Divide ground beef into 4 equal portions and shape into patties, seasoning both sides with salt and pepper. Heat a grill or skillet over medium-high heat. Cook patties for 3-4 minutes per side, adding cheese in the last minute to melt. Toast the buns lightly on the grill or in a skillet. Assemble burgers with lettuce, tomato, onion, and condiments of choice.

### 14. Shrimp Scampi
- description: Garlicky buttery shrimp tossed with linguine and white wine.
- tags: dinner, seafood, pasta
- prepTimeMinutes: 10, cookTimeMinutes: 15, servings: 4
- content:
  - Ingredients: 450g linguine; 500g large shrimp, peeled and deveined; 4 tbsp butter; 4 cloves garlic, minced; 1/2 cup dry white wine; 1/4 cup lemon juice; red pepper flakes to taste; chopped parsley for garnish
  - Instructions: Cook linguine in salted boiling water until al dente, then drain. Melt butter in a large skillet over medium heat and sauté garlic until fragrant. Add shrimp and cook until just pink, about 2 minutes per side, then remove from the skillet. Pour in white wine and lemon juice, and simmer to reduce slightly. Return shrimp to the skillet along with the cooked linguine, tossing to coat. Garnish with parsley and red pepper flakes before serving.

### 15. Vegetable Fried Rice
- description: Wok-fried rice with mixed vegetables and soy sauce.
- tags: dinner, vegan, asian, quick
- prepTimeMinutes: 10, cookTimeMinutes: 10, servings: 4
- content:
  - Ingredients: 3 cups cooked, cooled rice; 1 cup frozen peas and carrots; 1/2 onion, diced; 2 cloves garlic, minced; 3 tbsp soy sauce; 1 tbsp sesame oil; 2 tbsp vegetable oil; 2 green onions, sliced
  - Instructions: Heat vegetable oil in a wok or large skillet over high heat. Add onion and garlic, stir-frying until fragrant. Add peas and carrots and cook for 2 minutes. Add the cooked rice, breaking up any clumps, and stir-fry for 3-4 minutes. Drizzle in soy sauce and sesame oil, tossing to coat evenly. Garnish with green onions and serve hot.

### 16. Chicken Caesar Salad
- description: Grilled chicken over romaine with Caesar dressing and croutons.
- tags: lunch, salad
- prepTimeMinutes: 15, cookTimeMinutes: 12, servings: 2
- content:
  - Ingredients: 2 chicken breasts; 1 head romaine lettuce, chopped; 1/2 cup Caesar dressing; 1/2 cup croutons; 1/4 cup shaved parmesan; salt and pepper to taste
  - Instructions: Season chicken breasts with salt and pepper and grill or pan-sear until cooked through, about 6 minutes per side. Let chicken rest for 5 minutes, then slice. Toss romaine lettuce with Caesar dressing in a large bowl. Top with sliced chicken, croutons, and shaved parmesan. Serve immediately.

### 17. Homemade Guacamole
- description: Chunky avocado dip with lime, cilantro, and jalapeño.
- tags: snack, appetizer, vegan, no-cook
- prepTimeMinutes: 10, cookTimeMinutes: 0, servings: 4
- content:
  - Ingredients: 3 ripe avocados; 1 lime, juiced; 1/2 red onion, finely diced; 1 jalapeño, seeded and minced; 2 tbsp chopped cilantro; salt to taste
  - Instructions: Halve avocados, remove pits, and scoop flesh into a bowl. Mash to desired consistency with a fork. Stir in lime juice, red onion, jalapeño, and cilantro. Season with salt to taste. Serve immediately with tortilla chips.

### 18. Beef Chili
- description: Hearty slow-simmered chili with beans and warming spices.
- tags: dinner, comfort-food, spicy
- prepTimeMinutes: 15, cookTimeMinutes: 90, servings: 6
- content:
  - Ingredients: 700g ground beef; 1 onion, diced; 3 cloves garlic, minced; 2 tbsp chili powder; 1 tsp cumin; 800g crushed tomatoes; 2 cans kidney beans, drained; 1 cup beef stock; salt to taste
  - Instructions: Brown ground beef in a large pot over medium heat, then remove excess fat. Add onion and garlic, cooking until softened. Stir in chili powder and cumin and cook for 1 minute until fragrant. Add crushed tomatoes, kidney beans, and beef stock, stirring to combine. Simmer uncovered for at least 1.5 hours, stirring occasionally, until thickened. Season with salt to taste and serve with cornbread or rice.

### 19. Lemon Garlic Roasted Chicken
- description: Whole roasted chicken with lemon, garlic, and fresh herbs.
- tags: dinner, roast
- prepTimeMinutes: 15, cookTimeMinutes: 75, servings: 4
- content:
  - Ingredients: 1 whole chicken (about 1.8kg); 1 lemon, halved; 1 head garlic, halved; 3 tbsp olive oil; fresh rosemary and thyme sprigs; salt and pepper to taste
  - Instructions: Preheat oven to 200C (400F). Pat the chicken dry and season generously inside and out with salt and pepper. Stuff the cavity with lemon halves, garlic, and herb sprigs. Rub the outside of the chicken with olive oil. Roast for 70-80 minutes, until the internal temperature reaches 74C (165F) and the skin is golden. Let rest for 10 minutes before carving.

## Validation checklist

- [x] `mvn test` passes — the only existing test is the `contextLoads` smoke test; no test
      hardcodes a seeded recipe count.
- [x] Started the app against the in-memory H2 DB (on a throwaway port, since the folder's
      auto-started dev server already held 8080) and confirmed `GET /recipes` returns all
      20 recipes with the expected titles.
- [ ] Spot-check a couple of entries via Swagger UI (`/swagger-ui.html`) to confirm the
      Markdown `content` renders sensibly on the frontend's `RecipeDetailComponent`
      (which parses it with `marked`).
- [ ] No frontend changes are required — the generated API client and `RecipeService` are
      unaffected by seed data changes.
- [ ] The repo's already-running dev backend (started by the VSCode auto-launch task) is
      still on the old in-memory data — restart it (or just let the next `ng serve` /
      `mvn spring-boot:run` cycle pick it up) to see the new 20-recipe seed.
