package com.keaa.adminapi.category;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * /api/categories — CRUD for top-level product categories. Access (SUPER_ADMIN / ADMIN /
 * MARKETING) is enforced in SecurityConfig, matching the Product Categories access-matrix row.
 */
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryRepository repo;

    @GetMapping
    public List<Category> all() {
        return repo.findAllByOrderBySortOrderAscNameAsc();
    }

    @PostMapping
    public Category create(@Valid @RequestBody CategoryRequest req) {
        return repo.save(Category.builder()
                .name(req.name())
                .slug(slug(req))
                .description(req.description())
                .sortOrder(req.sortOrder())
                .active(req.active())
                .build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Category> update(@PathVariable Long id, @Valid @RequestBody CategoryRequest req) {
        return repo.findById(id).map(c -> {
            c.setName(req.name());
            c.setSlug(slug(req));
            c.setDescription(req.description());
            c.setSortOrder(req.sortOrder());
            c.setActive(req.active());
            return ResponseEntity.ok(repo.save(c));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /** Falls back to a slugified name when the caller leaves the slug blank. */
    private String slug(CategoryRequest req) {
        String s = req.slug() == null ? "" : req.slug().trim();
        if (!s.isEmpty()) return s;
        return req.name().toLowerCase().trim()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    public record CategoryRequest(
            @NotBlank String name,
            String slug,
            String description,
            int sortOrder,
            boolean active) {}
}
