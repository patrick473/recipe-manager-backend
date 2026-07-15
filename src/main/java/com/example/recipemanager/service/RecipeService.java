package com.example.recipemanager.service;

import com.example.recipemanager.dto.RecipeRequest;
import com.example.recipemanager.dto.RecipeResponse;
import com.example.recipemanager.exception.RecipeNotFoundException;
import com.example.recipemanager.model.Recipe;
import com.example.recipemanager.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for managing recipes.
 * Converts between the {@link Recipe} JPA entity and the {@link RecipeResponse} DTO.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecipeService {

    private final RecipeRepository repository;

    /** Return every recipe in ascending id order. */
    public List<RecipeResponse> findAll() {
        return repository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    /** Return a single recipe or throw 404. */
    public RecipeResponse findById(Long id) {
        return repository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new RecipeNotFoundException(id));
    }

    /** Persist a new recipe and return the persisted representation. */
    @Transactional
    public RecipeResponse create(RecipeRequest request) {
        Recipe entity = Recipe.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .content(request.getContent())
                .build();
        return toResponse(repository.save(entity));
    }

    /** Replace all mutable fields on an existing recipe. */
    @Transactional
    public RecipeResponse update(Long id, RecipeRequest request) {
        Recipe entity = repository.findById(id)
                .orElseThrow(() -> new RecipeNotFoundException(id));
        entity.setTitle(request.getTitle());
        entity.setDescription(request.getDescription());
        entity.setContent(request.getContent());
        return toResponse(repository.save(entity));
    }

    /** Delete a recipe by id, or throw 404 if it does not exist. */
    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new RecipeNotFoundException(id);
        }
        repository.deleteById(id);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private RecipeResponse toResponse(Recipe entity) {
        return RecipeResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .description(entity.getDescription())
                .content(entity.getContent())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
