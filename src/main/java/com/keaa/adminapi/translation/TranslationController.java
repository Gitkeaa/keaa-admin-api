package com.keaa.adminapi.translation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * /api/translations — CRUD for locale-string overrides. Access (SUPER_ADMIN / ADMIN /
 * MARKETING) is enforced in SecurityConfig, matching the Languages / Translations row.
 */
@RestController
@RequestMapping("/api/translations")
@RequiredArgsConstructor
public class TranslationController {

    private final TranslationRepository repo;

    @GetMapping
    public List<Translation> all() {
        return repo.findAllByOrderByTransKeyAscLangCodeAsc();
    }

    @PostMapping
    public Translation create(@Valid @RequestBody TranslationRequest req) {
        return repo.save(Translation.builder()
                .transKey(req.transKey().trim())
                .langCode(req.langCode().trim())
                .value(req.value())
                .build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Translation> update(@PathVariable Long id, @Valid @RequestBody TranslationRequest req) {
        return repo.findById(id).map(t -> {
            t.setTransKey(req.transKey().trim());
            t.setLangCode(req.langCode().trim());
            t.setValue(req.value());
            return ResponseEntity.ok(repo.save(t));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) return ResponseEntity.notFound().build();
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    public record TranslationRequest(
            @NotBlank String transKey,
            @NotBlank String langCode,
            String value) {}
}
