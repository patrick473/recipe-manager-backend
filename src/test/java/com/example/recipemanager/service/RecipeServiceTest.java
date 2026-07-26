package com.example.recipemanager.service;

import com.example.recipemanager.dto.RecipePageResponse;
import com.example.recipemanager.dto.RecipeResponse;
import com.example.recipemanager.model.Recipe;
import com.example.recipemanager.repository.RecipeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RecipeService#findAll(String, List, Pageable)}: verifies
 * the {@code Page<Recipe>} -&gt; {@link RecipePageResponse} mapping and that the
 * given {@link Pageable} is passed straight through to the repository.
 * {@link com.example.recipemanager.repository.RecipeSpecifications} query
 * behavior itself is covered by {@code RecipeRepositoryTest} against a real
 * database, not re-verified here with mocks.
 */
@ExtendWith(MockitoExtension.class)
class RecipeServiceTest {

    @Mock
    private RecipeRepository repository;

    @Mock
    private ImageStorageService imageStorageService;

    private RecipeService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new RecipeService(repository, imageStorageService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAllMapsPageOfEntitiesToRecipePageResponse() {
        Recipe recipe = Recipe.builder()
                .id(1L)
                .title("Banana Bread")
                .description("Moist and simple")
                .content("content")
                .tags(List.of("breakfast"))
                .prepTimeMinutes(10)
                .cookTimeMinutes(60)
                .servings(4)
                .build();

        Pageable pageable = PageRequest.of(0, 20);
        Page<Recipe> repositoryPage = new PageImpl<>(List.of(recipe), pageable, 1);

        when(repository.findAll(any(Specification.class), eq(pageable))).thenReturn(repositoryPage);

        RecipePageResponse result = service.findAll("banana", List.of("breakfast"), pageable);

        assertThat(result.getContent()).hasSize(1);
        RecipeResponse response = result.getContent().get(0);
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getTitle()).isEqualTo("Banana Bread");
        assertThat(response.getTags()).containsExactly("breakfast");
        assertThat(result.getPage()).isEqualTo(0);
        assertThat(result.getSize()).isEqualTo(20);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getTotalPages()).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAllPassesGivenPageableThroughToRepositoryAndHandlesEmptyResults() {
        Pageable pageable = PageRequest.of(2, 5);
        Page<Recipe> repositoryPage = new PageImpl<>(List.of(), pageable, 0);

        when(repository.findAll(any(Specification.class), eq(pageable))).thenReturn(repositoryPage);

        RecipePageResponse result = service.findAll(null, null, pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(5);
        assertThat(result.getTotalElements()).isEqualTo(0);
        assertThat(result.getTotalPages()).isEqualTo(0);
        verify(repository).findAll(any(Specification.class), eq(pageable));
    }
}
