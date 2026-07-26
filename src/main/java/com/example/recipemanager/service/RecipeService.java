package com.example.recipemanager.service;

import com.example.recipemanager.dto.RecipePageResponse;
import com.example.recipemanager.dto.RecipeRequest;
import com.example.recipemanager.dto.RecipeResponse;
import com.example.recipemanager.exception.RecipeNotFoundException;
import com.example.recipemanager.model.Recipe;
import com.example.recipemanager.repository.RecipeRepository;
import com.example.recipemanager.repository.RecipeSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Business logic for managing recipes.
 * Converts between the {@link Recipe} JPA entity and the {@link RecipeResponse} DTO.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecipeService {

    private final RecipeRepository repository;

    /**
     * Return a page of recipes matching the given filters, mapped to a
     * {@link RecipePageResponse}. {@code q} and {@code tags} are combined
     * with AND semantics when both are present; either may be blank/empty to
     * mean "no filter".
     */
    public RecipePageResponse findAll(String q, List<String> tags, Pageable pageable) {
        // Specification.allOf()/and() reject null elements outright, so only the
        // filters that actually apply (q/tags present) are combined; an empty
        // filter set falls back to Specification.allOf(List.of()), i.e. "match everything".
        List<Specification<Recipe>> activeSpecs = Stream.of(
                        RecipeSpecifications.titleOrDescriptionContains(q),
                        RecipeSpecifications.hasAnyTag(tags))
                .filter(Objects::nonNull)
                .toList();
        Specification<Recipe> spec = Specification.allOf(activeSpecs);

        Page<Recipe> page = repository.findAll(spec, pageable);

        List<RecipeResponse> content = page.getContent().stream()
                .map(this::toResponse)
                .toList();

        return RecipePageResponse.builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
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
                .tags(request.getTags() != null ? request.getTags() : List.of())
                .prepTimeMinutes(request.getPrepTimeMinutes())
                .cookTimeMinutes(request.getCookTimeMinutes())
                .servings(request.getServings())
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
        entity.setTags(request.getTags() != null ? request.getTags() : List.of());
        entity.setPrepTimeMinutes(request.getPrepTimeMinutes());
        entity.setCookTimeMinutes(request.getCookTimeMinutes());
        entity.setServings(request.getServings());
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
                .tags(entity.getTags())
                .prepTimeMinutes(entity.getPrepTimeMinutes())
                .cookTimeMinutes(entity.getCookTimeMinutes())
                .servings(entity.getServings())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
