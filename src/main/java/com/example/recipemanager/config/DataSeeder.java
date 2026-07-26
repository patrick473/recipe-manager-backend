package com.example.recipemanager.config;

import com.example.recipemanager.model.Recipe;
import com.example.recipemanager.repository.RecipeRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds a default recipe on startup so a fresh database is never empty.
 * Only runs when the table has no rows, so it is a no-op on restarts
 * against a persistent (e.g. PostgreSQL) database that already has data.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final RecipeRepository recipeRepository;

    public DataSeeder(RecipeRepository recipeRepository) {
        this.recipeRepository = recipeRepository;
    }

    @Override
    public void run(String... args) {
        if (recipeRepository.count() > 0) {
            return;
        }

        recipeRepository.save(Recipe.builder()
                .title("Classic Pancakes")
                .description("Fluffy breakfast pancakes from scratch.")
                .content("""
                        ## Ingredients

                        - 1 1/2 cups all-purpose flour
                        - 3 1/2 tsp baking powder
                        - 1 tsp salt
                        - 1 tbsp sugar
                        - 1 1/4 cups milk
                        - 1 egg
                        - 3 tbsp melted butter

                        ## Instructions

                        1. Whisk together flour, baking powder, salt, and sugar.
                        2. In a separate bowl, mix milk, egg, and melted butter.
                        3. Combine wet and dry ingredients, stirring until just mixed.
                        4. Pour 1/4 cup batter per pancake onto a hot, greased griddle.
                        5. Cook until bubbles form on the surface, then flip and cook until golden.
                        """)
                .tags(List.of("breakfast", "quick"))
                .prepTimeMinutes(10)
                .cookTimeMinutes(15)
                .servings(4)
                .build());
    }
}
