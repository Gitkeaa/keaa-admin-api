package com.keaa.adminapi.product;

import com.cloudinary.Cloudinary;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** /api/products — catalogue management with paging + search. */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductRepository productRepository;
    private final Cloudinary cloudinary;

    /** Cloudinary folder new product images land in — the FULL nested path to the folder the
     *  seeded catalogue lives in, so uploads never spawn a stray top-level folder. */
    @Value("${cloudinary.products-folder:1.Keaa Assets/Keaa products}")
    private String productsFolder;

    /** Upload a product image from the admin's device straight to Cloudinary; returns its URL.
     *  The Cloudinary public_id (display name) is derived from the product name so the asset reads
     *  as e.g. "ringlock-tower-a1b2c3" instead of a random string. */
    @PostMapping("/image")
    public Map<String, String> uploadImage(@RequestParam MultipartFile file,
                                           @RequestParam(required = false) String name) throws IOException {
        Map<String, Object> options = new HashMap<>();
        options.put("folder", productsFolder);
        options.put("resource_type", "image");
        String slug = slugify(name);
        if (slug.isEmpty()) {
            // No product name yet — fall back to the uploaded file's own name.
            options.put("use_filename", true);
            options.put("unique_filename", true);
        } else {
            // A readable name plus a short suffix so re-uploads for the same product never collide.
            options.put("public_id", slug + "-" + UUID.randomUUID().toString().substring(0, 6));
            options.put("overwrite", false);
        }
        Map<?, ?> res = cloudinary.uploader().upload(file.getBytes(), options);
        return Map.of("url", String.valueOf(res.get("secure_url")));
    }

    /** Lowercase, hyphenate and trim a product name into a Cloudinary-safe public_id fragment. */
    private static String slugify(String s) {
        if (s == null) return "";
        String slug = s.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        return slug.length() > 60 ? slug.substring(0, 60).replaceAll("-+$", "") : slug;
    }

    @GetMapping
    public Page<Product> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) String q) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id"));
        if (q != null && !q.isBlank()) {
            return productRepository
                    .findByNameContainingIgnoreCaseOrItemCodeContainingIgnoreCase(q, q, pageable);
        }
        return productRepository.findAll(pageable);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> one(@PathVariable Long id) {
        return productRepository.findById(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public Product create(@RequestBody Product product) {
        product.setId(null);
        return productRepository.save(product);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Product> update(@PathVariable Long id, @RequestBody Product body) {
        return productRepository.findById(id).map(p -> {
            p.setItemCode(body.getItemCode());
            p.setName(body.getName());
            p.setCategory(body.getCategory());
            p.setSubcategory(body.getSubcategory());
            p.setDescription(body.getDescription());
            p.setImageUrl(body.getImageUrl());
            return ResponseEntity.ok(productRepository.save(p));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        // Delete the product's Cloudinary image too, so removing a product doesn't leave an orphan
        // asset behind. Best-effort: a Cloudinary hiccup must not block the row from being deleted.
        productRepository.findById(id).ifPresent(p -> {
            String publicId = publicIdFromCloudinaryUrl(p.getImageUrl());
            if (publicId != null) {
                try { cloudinary.uploader().destroy(publicId, Map.of("invalidate", true)); } catch (Exception ignored) { /* keep going */ }
            }
            productRepository.deleteById(id);
        });
        return ResponseEntity.noContent().build();
    }

    /** Pull the Cloudinary public_id back out of a stored secure_url, or null if it isn't one. */
    static String publicIdFromCloudinaryUrl(String url) {
        if (url == null || !url.contains("res.cloudinary.com") || !url.contains("/upload/")) return null;
        String after = url.substring(url.indexOf("/upload/") + "/upload/".length());
        if (after.matches("^v\\d+/.*")) after = after.substring(after.indexOf('/') + 1); // drop version
        int dot = after.lastIndexOf('.');
        if (dot > after.lastIndexOf('/')) after = after.substring(0, dot);              // drop extension
        return java.net.URLDecoder.decode(after, java.nio.charset.StandardCharsets.UTF_8);
    }
}
