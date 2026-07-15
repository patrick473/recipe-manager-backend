package com.example.recipemanager.repository;

import com.example.recipemanager.model.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link Recipe} entities.
 * All five CRUD operations are provided by the parent interface.
 */
@Repository
public interface RecipeRepository extends JpaRepository<Recipe, Long> {
}
