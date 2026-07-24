package com.keaa.adminapi.feedback;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * /api/feedback — the feedback drawer on the public site.
 *
 * The POST is public and unauthenticated, like the other website forms; reading and triaging
 * still require an admin login. The frontend that calls this is
 * `src/components/FeedbackWidget.jsx` in the website repo, and the contract is written up in
 * its BACKEND_FEEDBACK.md.
 *
 * Unlike the catalogue gate, nothing here is fire-and-forget: the widget shows the visitor a
 * real error when this fails, so a 4xx is information they act on rather than a silent loss.
 */
@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private static final Set<String> TYPES = Set.of("suggestion", "feedback", "issue", "compliment");

    /**
     * The widget caps the message at 200 words. A client cap is not a control, so the column is
     * capped here too — generously, since 200 words of long compound terms can run past 2000
     * characters and truncating a genuine report would be worse than storing a long one.
     */
    private static final int MAX_MESSAGE_CHARS = 4000;
    private static final int MAX_PAGE_URL_CHARS = 512;

    private final FeedbackRepository repository;

    /** Authenticated: the Site Feedback screen reads this. Newest first. */
    @GetMapping
    public List<Feedback> all() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    /** Public (no auth), like /api/rfq, /api/contact, /api/careers and /api/catalogue-requests. */
    @PostMapping
    public ResponseEntity<?> submit(@RequestBody FeedbackRequest req) {
        if (req.rating() == null || req.rating() < 1 || req.rating() > 5) {
            return ResponseEntity.badRequest().body(Map.of("error", "Rating must be between 1 and 5."));
        }
        if (isBlank(req.message())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Message is required."));
        }

        boolean consent = Boolean.TRUE.equals(req.contactConsent());
        // Asking to be contacted without leaving an address is the one combination that cannot be
        // honoured, so it is the only case where the address is mandatory.
        if (consent && isBlank(req.email())) {
            return ResponseEntity.badRequest().body(Map.of("error", "An email address is required to reply."));
        }

        // An unknown type falls back rather than 400s: the visitor's message is worth more than
        // strictness about a radio button, and only this server's own frontend sets the field.
        String type = req.type() == null ? "" : req.type().trim().toLowerCase();
        if (!TYPES.contains(type)) type = "feedback";

        Feedback saved = repository.save(Feedback.builder()
                .rating(req.rating())
                .type(type)
                .message(truncate(req.message().trim(), MAX_MESSAGE_CHARS))
                .name(trim(req.name()))
                .email(trim(req.email()))
                .contactConsent(consent)
                .pageUrl(truncate(trim(req.pageUrl()), MAX_PAGE_URL_CHARS))
                .build());

        // Only the id goes back, as with the catalogue gate — the caller is an anonymous browser
        // and has no use for the stored row.
        return ResponseEntity.ok(Map.of("id", saved.getId()));
    }

    /** Authenticated: move a row through new → reviewed → actioned → closed. */
    @PatchMapping("/{id}/status")
    public ResponseEntity<Feedback> updateStatus(@PathVariable Long id, @RequestBody StatusRequest body) {
        return repository.findById(id).map(f -> {
            f.setStatus(body.status());
            return ResponseEntity.ok(repository.save(f));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    /**
     * Blank collapses to NULL, so "not given" has exactly ONE representation in the column.
     * The widget always sends "" for an omitted optional field while a direct API call omits it
     * entirely, and without this the table carries both "" and NULL for the same fact — which
     * makes `WHERE email IS NULL` quietly wrong for whoever builds the admin list.
     */
    private static String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    public record FeedbackRequest(Integer rating, String type, String message, String name,
                                  String email, Boolean contactConsent, String pageUrl) {}

    public record StatusRequest(String status) {}
}
