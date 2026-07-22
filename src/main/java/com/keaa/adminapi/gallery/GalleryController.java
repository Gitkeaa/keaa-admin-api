package com.keaa.adminapi.gallery;

import com.cloudinary.Cloudinary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * /api/gallery — CRUD for the public gallery's image list. Access (SUPER_ADMIN / ADMIN /
 * MARKETING) is enforced in SecurityConfig, matching the Gallery / Media access-matrix row.
 */
@RestController
@RequestMapping("/api/gallery")
@RequiredArgsConstructor
public class GalleryController {

    private final GalleryRepository repo;
    private final Cloudinary cloudinary;

    @Value("${cloudinary.gallery-folder:1.Keaa Assets/Keaa Gallery}")
    private String galleryFolder;

    @GetMapping
    public List<GalleryItem> all() {
        return repo.findAllByOrderBySortOrderAscIdAsc();
    }

    /** Upload a gallery photo from the admin's device to Cloudinary; returns its public_id
     *  (the value stored as cloudinaryId and rendered by cldImage). */
    @PostMapping("/image")
    public Map<String, String> uploadImage(@RequestParam MultipartFile file) throws IOException {
        Map<String, Object> options = new HashMap<>();
        options.put("folder", galleryFolder);
        options.put("resource_type", "image");
        options.put("use_filename", true);
        options.put("unique_filename", true);
        Map<?, ?> res = cloudinary.uploader().upload(file.getBytes(), options);
        return Map.of("publicId", String.valueOf(res.get("public_id")));
    }

    @PostMapping
    public GalleryItem create(@Valid @RequestBody GalleryRequest req) {
        return repo.save(GalleryItem.builder()
                .cloudinaryId(req.cloudinaryId().trim())
                .category(req.category())
                .alt(req.alt())
                .sortOrder(req.sortOrder())
                .active(req.active())
                .build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<GalleryItem> update(@PathVariable Long id, @Valid @RequestBody GalleryRequest req) {
        return repo.findById(id).map(g -> {
            g.setCloudinaryId(req.cloudinaryId().trim());
            g.setCategory(req.category());
            g.setAlt(req.alt());
            g.setSortOrder(req.sortOrder());
            g.setActive(req.active());
            return ResponseEntity.ok(repo.save(g));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        GalleryItem g = repo.findById(id).orElse(null);
        if (g == null) return ResponseEntity.notFound().build();
        // Remove the image from Cloudinary too (its public_id IS the stored cloudinaryId).
        // Best-effort: a Cloudinary hiccup must not block the row from being deleted.
        if (g.getCloudinaryId() != null && !g.getCloudinaryId().isBlank()) {
            try { cloudinary.uploader().destroy(g.getCloudinaryId(), Map.of("invalidate", true)); } catch (Exception ignored) { /* keep going */ }
        }
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    public record GalleryRequest(
            @NotBlank String cloudinaryId,
            String category,
            String alt,
            int sortOrder,
            boolean active) {}
}
