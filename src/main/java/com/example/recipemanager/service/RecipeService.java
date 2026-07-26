package com.example.recipemanager.service;

import com.example.recipemanager.dto.RecipePageResponse;
import com.example.recipemanager.dto.RecipeRequest;
import com.example.recipemanager.dto.RecipeResponse;
import com.example.recipemanager.exception.ImageNotFoundException;
import com.example.recipemanager.exception.InvalidImageException;
import com.example.recipemanager.exception.RecipeNotFoundException;
import com.example.recipemanager.model.Recipe;
import com.example.recipemanager.repository.RecipeRepository;
import com.example.recipemanager.repository.RecipeSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Business logic for managing recipes.
 * Converts between the {@link Recipe} JPA entity and the {@link RecipeResponse} DTO.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecipeService {

    private static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");

    private final RecipeRepository repository;
    private final ImageStorageService imageStorageService;

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

    /**
     * Uploads (or replaces) a recipe's hero image: validates the declared
     * content type and sniffs the actual bytes via {@code ImageIO} before
     * storing anything, deletes the previously stored file (if any) so
     * replacing never leaves an orphaned file on disk, then persists the
     * new filename.
     */
    @Transactional
    public RecipeResponse uploadImage(Long id, MultipartFile file) {
        Recipe entity = repository.findById(id)
                .orElseThrow(() -> new RecipeNotFoundException(id));

        validateImage(file);

        if (entity.getImageFilename() != null) {
            imageStorageService.delete(entity.getImageFilename());
        }

        String filename = imageStorageService.store(id, file);
        entity.setImageFilename(filename);
        return toResponse(repository.save(entity));
    }

    /**
     * Removes a recipe's hero image, if it has one. A recipe with no image
     * is a no-op that still returns 200 with the (unchanged) current
     * representation — idempotent, same spirit as the rest of this API.
     */
    @Transactional
    public RecipeResponse deleteImage(Long id) {
        Recipe entity = repository.findById(id)
                .orElseThrow(() -> new RecipeNotFoundException(id));

        if (entity.getImageFilename() != null) {
            imageStorageService.delete(entity.getImageFilename());
            entity.setImageFilename(null);
        }
        return toResponse(repository.save(entity));
    }

    /**
     * Loads a recipe's hero image bytes plus the headers needed to serve them.
     * Both "recipe doesn't exist" and "recipe exists but has no image" collapse
     * to the same {@link ImageNotFoundException} (404) — this read-only,
     * byte-streaming endpoint has no reason to distinguish the two.
     */
    public RecipeImage loadImage(Long id) {
        Recipe entity = repository.findById(id)
                .orElseThrow(() -> new ImageNotFoundException(id));

        String filename = entity.getImageFilename();
        if (filename == null) {
            throw new ImageNotFoundException(id);
        }

        return new RecipeImage(
                imageStorageService.load(filename), mediaTypeForFilename(filename), "\"" + filename + "\"");
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Validates an uploaded image in the order the spec requires (size is
     * enforced upstream by {@code spring.servlet.multipart.max-*-size}, which
     * rejects oversized uploads before this method ever runs): declared
     * content type first as a cheap reject, then content sniffing so a client
     * can't just lie about the header.
     */
    private void validateImage(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new InvalidImageException("Unsupported content type: " + contentType);
        }

        try {
            if (!matchesDeclaredImageType(contentType, file.getBytes())) {
                throw new InvalidImageException("File content is not a valid image");
            }
        } catch (IOException e) {
            throw new InvalidImageException("Unable to read uploaded file");
        }
    }

    /**
     * Content-sniffs the actual bytes rather than trusting {@code contentType}.
     * For jpeg/png, {@code ImageIO.read} must return a decodable image — this is
     * the literal spec check. For webp, this falls back to verifying the
     * RIFF/WEBP container signature instead: the JDK's built-in {@code ImageIO}
     * has no WebP reader at all (verified against
     * {@code ImageIO.getReaderFormatNames()}: only jpeg/png/gif/bmp/tiff/wbmp are
     * registered), so a literal {@code ImageIO.read} sniff would reject every
     * genuine WebP upload. The signature check can't fully decode pixels, but it
     * still defeats a client that renames an arbitrary file to {@code .webp} or
     * lies about {@code Content-Type} — this validation step's actual threat
     * model — without adding a WebP-capable ImageIO plugin as a new dependency.
     */
    private static boolean matchesDeclaredImageType(String contentType, byte[] bytes) throws IOException {
        if ("image/webp".equalsIgnoreCase(contentType)) {
            return isWebP(bytes);
        }
        return ImageIO.read(new ByteArrayInputStream(bytes)) != null;
    }

    private static boolean isWebP(byte[] bytes) {
        return bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
    }

    private static MediaType mediaTypeForFilename(String filename) {
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "png" -> MediaType.IMAGE_PNG;
            case "webp" -> MediaType.valueOf("image/webp");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

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
                .imageUrl(entity.getImageFilename() != null ? "/recipes/" + entity.getId() + "/image" : null)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
