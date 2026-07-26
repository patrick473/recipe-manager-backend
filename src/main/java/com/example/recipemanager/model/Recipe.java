package com.example.recipemanager.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistent entity representing a recipe.
 */
@Entity
@Table(name = "recipes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Short human-readable title shown in the recipe list.
     */
    @Column(nullable = false, length = 255)
    private String title;

    /**
     * Optional one-line summary displayed below the title.
     */
    @Column(length = 512)
    private String description;

    /**
     * Full recipe body in Markdown format.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * Optional freeform labels for categorizing and filtering recipes.
     */
    @ElementCollection
    @CollectionTable(name = "recipe_tags", joinColumns = @JoinColumn(name = "recipe_id"))
    @Column(name = "tag")
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    /**
     * Estimated preparation time in minutes.
     */
    private Integer prepTimeMinutes;

    /**
     * Estimated cooking time in minutes.
     */
    private Integer cookTimeMinutes;

    /**
     * Number of servings this recipe yields.
     */
    private Integer servings;

    /**
     * Generated filename (e.g. {@code 3f2a91e0-....jpg}) of the recipe's hero image,
     * as stored by {@link com.example.recipemanager.service.ImageStorageService}.
     * {@code null} when the recipe has no image. Never a full path or the
     * client-supplied filename.
     */
    @Column(name = "image_filename")
    private String imageFilename;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
