package com.keaa.adminapi.download;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

/**
 * /api/downloads — catalogues, datasheets and certificates.
 *
 * Files are written to {app.upload.dir}/downloads on the server's own disk (the storage
 * choice for this build), under a random name so two uploads with the same original file
 * name never collide. Metadata lives in the DB.
 *
 * Access is enforced in SecurityConfig: any signed-in staff member may list the files and
 * download one (the dashboard's Resource Library shows them to the whole team), while upload
 * and delete stay with SUPER_ADMIN / SENIOR_ADMIN / ADMIN / BUSINESS_DEVELOPMENT. The
 * file-serving GET sits under the same path, so a download link carries the admin cookie
 * (a top-level GET, allowed by SameSite=Lax).
 */
@RestController
@RequestMapping("/api/downloads")
@RequiredArgsConstructor
public class DownloadController {

    private final DownloadRepository repo;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private Path dir() throws IOException {
        Path d = Paths.get(uploadDir, "downloads");
        Files.createDirectories(d);
        return d;
    }

    @GetMapping
    public List<DownloadItem> all() {
        return repo.findAllByOrderByUploadedAtDesc();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@RequestParam String title,
                                    @RequestParam(required = false) String category,
                                    @RequestParam MultipartFile file) throws IOException {
        if (file.isEmpty()) return ResponseEntity.badRequest().body("File is empty.");
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String safe = original.replaceAll("[^A-Za-z0-9._-]", "_");
        String stored = UUID.randomUUID() + "_" + safe;
        file.transferTo(dir().resolve(stored));

        DownloadItem saved = repo.save(DownloadItem.builder()
                .title(title)
                .category(category)
                .fileName(original)
                .storedName(stored)
                .contentType(file.getContentType())
                .size(file.getSize())
                .build());
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> download(@PathVariable Long id) throws IOException {
        DownloadItem d = repo.findById(id).orElse(null);
        if (d == null) return ResponseEntity.notFound().build();
        Resource resource = new UrlResource(dir().resolve(d.getStoredName()).toUri());
        if (!resource.exists() || !resource.isReadable()) return ResponseEntity.notFound().build();
        String ct = d.getContentType() != null ? d.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(ct))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + d.getFileName() + "\"")
                .body(resource);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) throws IOException {
        DownloadItem d = repo.findById(id).orElse(null);
        if (d == null) return ResponseEntity.notFound().build();
        Files.deleteIfExists(dir().resolve(d.getStoredName()));
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
