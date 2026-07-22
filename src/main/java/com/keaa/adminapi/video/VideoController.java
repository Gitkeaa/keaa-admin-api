package com.keaa.adminapi.video;

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
 * /api/videos — the site's videos (hero / project films). Same CRUD shape as the gallery, but
 * uploads go to Cloudinary as VIDEO assets in the hero-videos folder. Access is enforced in
 * SecurityConfig (admin tiers + Business Development).
 */
@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoRepository repo;
    private final Cloudinary cloudinary;

    @Value("${cloudinary.videos-folder:1.Keaa Assets/1.Keaa Hero page Videos}")
    private String videosFolder;

    @GetMapping
    public List<VideoItem> all() {
        return repo.findAllByOrderBySortOrderAscIdAsc();
    }

    /** Upload a video from the admin's device to Cloudinary; returns its public_id. */
    @PostMapping("/video")
    public Map<String, String> uploadVideo(@RequestParam MultipartFile file) throws IOException {
        Map<String, Object> options = new HashMap<>();
        options.put("folder", videosFolder);
        options.put("resource_type", "video");
        options.put("use_filename", true);
        options.put("unique_filename", true);
        Map<?, ?> res = cloudinary.uploader().upload(file.getBytes(), options);
        return Map.of("publicId", String.valueOf(res.get("public_id")));
    }

    @PostMapping
    public VideoItem create(@Valid @RequestBody VideoRequest req) {
        return repo.save(VideoItem.builder()
                .cloudinaryId(req.cloudinaryId().trim()).title(req.title()).category(req.category())
                .sortOrder(req.sortOrder()).active(req.active()).build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<VideoItem> update(@PathVariable Long id, @Valid @RequestBody VideoRequest req) {
        return repo.findById(id).map(v -> {
            v.setCloudinaryId(req.cloudinaryId().trim());
            v.setTitle(req.title());
            v.setCategory(req.category());
            v.setSortOrder(req.sortOrder());
            v.setActive(req.active());
            return ResponseEntity.ok(repo.save(v));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        VideoItem v = repo.findById(id).orElse(null);
        if (v == null) return ResponseEntity.notFound().build();
        // Remove the video from Cloudinary too (resource_type video). Best-effort.
        if (v.getCloudinaryId() != null && !v.getCloudinaryId().isBlank()) {
            try {
                cloudinary.uploader().destroy(v.getCloudinaryId(), Map.of("invalidate", true, "resource_type", "video"));
            } catch (Exception ignored) { /* keep going */ }
        }
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    public record VideoRequest(@NotBlank String cloudinaryId, String title, String category, int sortOrder, boolean active) {}
}
