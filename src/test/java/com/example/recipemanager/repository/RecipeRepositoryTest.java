package com.example.recipemanager.repository;

import com.example.recipemanager.model.Recipe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link RecipeSpecifications} and the {@code findAll(Specification, Pageable)}
 * query it backs, against a real H2 instance (this kind of query logic is exactly
 * the sort of thing that is subtly wrong if only unit-tested against mocks).
 */
@DataJpaTest
class RecipeRepositoryTest {

    @Autowired
    private RecipeRepository repository;

    private Recipe bananaPancakes;
    private Recipe veggieSoup;
    private Recipe chocolateCake;
    private Recipe garlicBread;
    private Recipe grilledCheese;

    @BeforeEach
    void seedRecipes() {
        bananaPancakes = repository.save(recipe(
                "Banana Pancakes", "Fluffy breakfast favorite", List.of("breakfast", "quick"), 10, 15));
        veggieSoup = repository.save(recipe(
                "Veggie Soup", "A warm bowl with banana peppers", List.of("soup", "quick"), null, 30));
        chocolateCake = repository.save(recipe(
                "Chocolate Cake", "Rich and decadent dessert", List.of("dessert", "quick"), 20, 45));
        garlicBread = repository.save(recipe(
                "Garlic Bread", "Crispy and buttery", List.of("side"), 5, null));
        grilledCheese = repository.save(recipe(
                "Grilled Cheese", "Simple and satisfying", List.of("quick"), 2, 8));
    }

    private static Recipe recipe(String title, String description, List<String> tags,
                                  Integer prepTimeMinutes, Integer cookTimeMinutes) {
        return Recipe.builder()
                .title(title)
                .description(description)
                .content("Some content")
                .tags(tags)
                .prepTimeMinutes(prepTimeMinutes)
                .cookTimeMinutes(cookTimeMinutes)
                .servings(4)
                .build();
    }

    private Page<Recipe> find(Specification<Recipe> spec, Pageable pageable) {
        return repository.findAll(spec, pageable);
    }

    // -------------------------------------------------------------------------
    // q — title/description substring match
    // -------------------------------------------------------------------------

    @Test
    void qMatchesTitleOnly() {
        Page<Recipe> page = find(RecipeSpecifications.titleOrDescriptionContains("Pancakes"), Pageable.unpaged());
        assertThat(page.getContent()).containsExactly(bananaPancakes);
    }

    @Test
    void qMatchesDescriptionOnly() {
        Page<Recipe> page = find(RecipeSpecifications.titleOrDescriptionContains("peppers"), Pageable.unpaged());
        assertThat(page.getContent()).containsExactly(veggieSoup);
    }

    @Test
    void qMatchesNeitherTitleNorDescription() {
        Page<Recipe> page = find(
                RecipeSpecifications.titleOrDescriptionContains("xyz-not-present"), Pageable.unpaged());
        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void qIsCaseInsensitive() {
        Page<Recipe> page = find(RecipeSpecifications.titleOrDescriptionContains("BANANA"), Pageable.unpaged());
        assertThat(page.getContent()).containsExactlyInAnyOrder(bananaPancakes, veggieSoup);
    }

    @Test
    void blankQMeansNoFilter() {
        Page<Recipe> page = find(RecipeSpecifications.titleOrDescriptionContains("   "), Pageable.unpaged());
        assertThat(page.getContent()).hasSize(5);
    }

    @Test
    void nullQMeansNoFilter() {
        Page<Recipe> page = find(RecipeSpecifications.titleOrDescriptionContains(null), Pageable.unpaged());
        assertThat(page.getContent()).hasSize(5);
    }

    // -------------------------------------------------------------------------
    // tags — OR semantics, no duplicate rows
    // -------------------------------------------------------------------------

    @Test
    void tagsMatchesAnyRequestedTag() {
        Page<Recipe> page = find(RecipeSpecifications.hasAnyTag(List.of("quick")), Pageable.unpaged());
        assertThat(page.getContent())
                .containsExactlyInAnyOrder(bananaPancakes, veggieSoup, chocolateCake, grilledCheese);
    }

    @Test
    void tagsOrMatchDoesNotReturnDuplicateRows() {
        // chocolateCake matches both "quick" and "dessert" — must still appear only once.
        Page<Recipe> page = find(RecipeSpecifications.hasAnyTag(List.of("quick", "dessert")), Pageable.unpaged());
        assertThat(page.getContent())
                .doesNotHaveDuplicates()
                .containsExactlyInAnyOrder(bananaPancakes, veggieSoup, chocolateCake, grilledCheese);
    }

    @Test
    void emptyTagsListMeansNoFilter() {
        Page<Recipe> page = find(RecipeSpecifications.hasAnyTag(List.of()), Pageable.unpaged());
        assertThat(page.getContent()).hasSize(5);
    }

    @Test
    void blankTagEntriesAreIgnored() {
        Page<Recipe> page = find(RecipeSpecifications.hasAnyTag(Arrays.asList("", "   ")), Pageable.unpaged());
        assertThat(page.getContent()).hasSize(5);
    }

    @Test
    void nullTagsMeansNoFilter() {
        Page<Recipe> page = find(RecipeSpecifications.hasAnyTag(null), Pageable.unpaged());
        assertThat(page.getContent()).hasSize(5);
    }

    // -------------------------------------------------------------------------
    // q AND tags combined
    // -------------------------------------------------------------------------

    @Test
    void qAndTagsCombinedWithAndSemantics() {
        Specification<Recipe> spec = Specification.allOf(
                RecipeSpecifications.titleOrDescriptionContains("banana"),
                RecipeSpecifications.hasAnyTag(List.of("soup")));
        Page<Recipe> page = find(spec, Pageable.unpaged());
        assertThat(page.getContent()).containsExactly(veggieSoup);
    }

    // -------------------------------------------------------------------------
    // Pagination
    // -------------------------------------------------------------------------

    @Test
    void paginationTotalsAreCorrect() {
        Page<Recipe> page = find(null, PageRequest.of(0, 2, Sort.by("title")));
        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.getContent()).hasSize(2);
    }

    @Test
    void lastPageHoldsTheRemainder() {
        Page<Recipe> page = find(null, PageRequest.of(2, 2, Sort.by("title")));
        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void pageBeyondLastPageReturnsEmptyContentNotError() {
        Page<Recipe> page = find(null, PageRequest.of(10, 2, Sort.by("title")));
        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(5);
    }

    // -------------------------------------------------------------------------
    // Sorting
    // -------------------------------------------------------------------------

    @Test
    void sortsByTitleAscending() {
        Page<Recipe> page = find(null, PageRequest.of(0, 10, Sort.by(Sort.Order.asc("title"))));
        assertThat(page.getContent()).extracting(Recipe::getTitle)
                .containsExactly(
                        "Banana Pancakes", "Chocolate Cake", "Garlic Bread", "Grilled Cheese", "Veggie Soup");
    }

    @Test
    void sortsByTitleDescending() {
        Page<Recipe> page = find(null, PageRequest.of(0, 10, Sort.by(Sort.Order.desc("title"))));
        assertThat(page.getContent()).extracting(Recipe::getTitle)
                .containsExactly(
                        "Veggie Soup", "Grilled Cheese", "Garlic Bread", "Chocolate Cake", "Banana Pancakes");
    }

    @Test
    void prepTimeMinutesSortsNullsLastAscending() {
        Sort.Order order = Sort.Order.asc("prepTimeMinutes").nullsLast();
        Page<Recipe> page = find(null, PageRequest.of(0, 10, Sort.by(order)));
        assertThat(page.getContent()).extracting(Recipe::getPrepTimeMinutes)
                .containsExactly(2, 5, 10, 20, null);
    }

    @Test
    void prepTimeMinutesSortsNullsLastDescending() {
        Sort.Order order = Sort.Order.desc("prepTimeMinutes").nullsLast();
        Page<Recipe> page = find(null, PageRequest.of(0, 10, Sort.by(order)));
        assertThat(page.getContent()).extracting(Recipe::getPrepTimeMinutes)
                .containsExactly(20, 10, 5, 2, null);
    }

    @Test
    void cookTimeMinutesSortsNullsLastAscending() {
        Sort.Order order = Sort.Order.asc("cookTimeMinutes").nullsLast();
        Page<Recipe> page = find(null, PageRequest.of(0, 10, Sort.by(order)));
        assertThat(page.getContent()).extracting(Recipe::getCookTimeMinutes)
                .containsExactly(8, 15, 30, 45, null);
    }

    @Test
    void cookTimeMinutesSortsNullsLastDescending() {
        Sort.Order order = Sort.Order.desc("cookTimeMinutes").nullsLast();
        Page<Recipe> page = find(null, PageRequest.of(0, 10, Sort.by(order)));
        assertThat(page.getContent()).extracting(Recipe::getCookTimeMinutes)
                .containsExactly(45, 30, 15, 8, null);
    }

    @Test
    void createdAtSortsAscendingMonotonically() {
        Page<Recipe> page = find(null, PageRequest.of(0, 10, Sort.by(Sort.Order.asc("createdAt"))));
        assertMonotonic(page.getContent().stream().map(Recipe::getCreatedAt).toList(), true);
    }

    @Test
    void updatedAtSortsDescendingMonotonically() {
        Page<Recipe> page = find(null, PageRequest.of(0, 10, Sort.by(Sort.Order.desc("updatedAt"))));
        assertMonotonic(page.getContent().stream().map(Recipe::getUpdatedAt).toList(), false);
    }

    private static void assertMonotonic(List<Instant> values, boolean ascending) {
        for (int i = 1; i < values.size(); i++) {
            if (ascending) {
                assertThat(values.get(i)).isAfterOrEqualTo(values.get(i - 1));
            } else {
                assertThat(values.get(i)).isBeforeOrEqualTo(values.get(i - 1));
            }
        }
    }
}
