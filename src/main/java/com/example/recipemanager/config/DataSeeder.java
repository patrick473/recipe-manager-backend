package com.example.recipemanager.config;

import com.example.recipemanager.dto.RecipeRequest;
import com.example.recipemanager.model.Recipe;
import com.example.recipemanager.model.User;
import com.example.recipemanager.repository.RecipeRepository;
import com.example.recipemanager.repository.UserRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/**
 * Seeds recipes from the bundled {@code data/mock-recipes.json} classpath resource on
 * startup so a fresh database is never empty. Only runs when the table has no rows, so
 * it is a no-op on restarts against a persistent (e.g. PostgreSQL) database that already
 * has data.
 * <p>
 * Every recipe now requires an owner (see AUTHENTICATION_SPEC.md Part 3), so seeding
 * attaches all mock recipes to a dedicated, unguessable-password "seed-data" account
 * rather than leaving them unowned. This account is a dev convenience only — nobody is
 * meant to log into it — and is created once (if absent) alongside the recipes.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final String SEED_FILE = "data/mock-recipes.json";
    private static final String SEED_USERNAME = "seed-data";

    private final RecipeRepository recipeRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    public DataSeeder(
            RecipeRepository recipeRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper) {
        this.recipeRepository = recipeRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws IOException {
        if (recipeRepository.count() > 0) {
            return;
        }

        User seedOwner = userRepository.findByUsername(SEED_USERNAME)
                .orElseGet(() -> userRepository.save(User.builder()
                        .username(SEED_USERNAME)
                        // Random, never-shared password: this account exists only to own
                        // seeded dev data, not to be logged into.
                        .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                        .build()));

        try (InputStream in = new ClassPathResource(SEED_FILE).getInputStream()) {
            List<RecipeRequest> seedRecipes = objectMapper.readValue(in, new TypeReference<>() {});
            recipeRepository.saveAll(seedRecipes.stream().map(request -> toEntity(request, seedOwner)).toList());
        }
    }

    private Recipe toEntity(RecipeRequest request, User owner) {
        return Recipe.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .content(request.getContent())
                .tags(request.getTags() != null ? request.getTags() : List.of())
                .prepTimeMinutes(request.getPrepTimeMinutes())
                .cookTimeMinutes(request.getCookTimeMinutes())
                .servings(request.getServings())
                .owner(owner)
                .build();
    }
}
