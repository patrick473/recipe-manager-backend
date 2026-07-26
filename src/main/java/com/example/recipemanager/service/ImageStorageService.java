package com.example.recipemanager.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Storage backend for recipe hero images. Deliberately minimal so that
 * swapping local disk for e.g. S3 later is a new implementation plus a
 * Spring profile, not a rewrite of {@link RecipeService}/the controller.
 */
public interface ImageStorageService {

    /**
     * Stores the given file's bytes under a newly generated, content-addressed
     * filename (never the client-supplied filename) and returns that filename.
     */
    String store(Long recipeId, MultipartFile file);

    /** Deletes the stored file with the given filename, if present. */
    void delete(String filename);

    /** Loads the stored file with the given filename as a {@link Resource}. */
    Resource load(String filename);
}
