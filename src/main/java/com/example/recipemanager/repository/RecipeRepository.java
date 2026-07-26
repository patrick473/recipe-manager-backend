package com.example.recipemanager.repository;

import com.example.recipemanager.model.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link Recipe} entities.
 * All five CRUD operations are provided by the parent interface;
 * {@link JpaSpecificationExecutor} adds {@code findAll(Specification, Pageable)}
 * for the filterable/paginated {@code GET /recipes} query, backed by
 * {@link RecipeSpecifications}.
 */
@Repository
public interface RecipeRepository extends JpaRepository<Recipe, Long>, JpaSpecificationExecutor<Recipe> {
}
