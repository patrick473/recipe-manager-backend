package com.example.recipemanager.config;

import com.example.recipemanager.dto.RecipeRequest;
import com.example.recipemanager.model.Recipe;
import com.example.recipemanager.repository.RecipeRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Seeds recipes from the bundled {@code data/mock-recipes.json} classpath resource on
 * startup so a fresh database is never empty. Only runs when the table has no rows, so
 * it is a no-op on restarts against a persistent (e.g. PostgreSQL) database that already
 * has data.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final String SEED_FILE = "data/mock-recipes.json";

    private final RecipeRepository recipeRepository;
    private final ObjectMapper objectMapper;

    public DataSeeder(RecipeRepository recipeRepository, ObjectMapper objectMapper) {
        this.recipeRepository = recipeRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws IOException {
        if (recipeRepository.count() > 0) {
            return;
        }

        try (InputStream in = new ClassPathResource(SEED_FILE).getInputStream()) {
            List<RecipeRequest> seedRecipes = objectMapper.readValue(in, new TypeReference<>() {});
            recipeRepository.saveAll(seedRecipes.stream().map(this::toEntity).toList());
        }
    }

    private Recipe toEntity(RecipeRequest request) {
        return Recipe.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .content(request.getContent())
                .tags(request.getTags() != null ? request.getTags() : List.of())
                .prepTimeMinutes(request.getPrepTimeMinutes())
                .cookTimeMinutes(request.getCookTimeMinutes())
                .servings(request.getServings())
                .build();
    }
}
