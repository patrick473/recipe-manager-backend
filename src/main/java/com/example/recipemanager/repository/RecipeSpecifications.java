package com.example.recipemanager.repository;

import com.example.recipemanager.model.Recipe;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

/**
 * Small, composable {@link Specification} building blocks for the filterable
 * {@code GET /recipes} query. Each method returns {@code null} when its input
 * is blank/empty so it can be dropped from the combined specification without
 * special-casing the caller (an empty {@code q}/{@code tags} means "match
 * everything", not "match nothing").
 */
public final class RecipeSpecifications {

    private RecipeSpecifications() {
    }

    /**
     * Case-insensitive substring match against {@code title} OR {@code description}.
     * Returns {@code null} (no restriction) when {@code q} is blank or absent.
     */
    public static Specification<Recipe> titleOrDescriptionContains(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String pattern = "%" + q.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern),
                cb.like(cb.lower(root.get("description")), pattern));
    }

    /**
     * Matches recipes having any (OR semantics) of the given tags, via a
     * correlated {@code EXISTS} subquery against the {@code recipe_tags} join
     * table. Deliberately avoids joining {@code tags} on the main query root:
     * a direct join against an {@code @ElementCollection} multiplies result
     * rows per matching tag, which breaks {@code Pageable}'s database-level
     * {@code LIMIT}/{@code OFFSET} pagination. The {@code EXISTS} subquery
     * filters without ever widening the outer row set.
     * <p>
     * Returns {@code null} (no restriction) when {@code tags} is {@code null},
     * empty, or contains only blank entries.
     */
    public static Specification<Recipe> hasAnyTag(List<String> tags) {
        if (tags == null) {
            return null;
        }
        List<String> filtered = tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::trim)
                .toList();
        if (filtered.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> {
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<Recipe> subRoot = subquery.correlate(root);
            Join<Recipe, String> tagJoin = subRoot.join("tags");
            subquery.select(cb.literal(1L));
            subquery.where(tagJoin.in(filtered));
            return cb.exists(subquery);
        };
    }
}
