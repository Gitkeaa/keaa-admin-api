package com.keaa.adminapi.catalogue;

import com.keaa.adminapi.inquiry.Inquiry;
import com.keaa.adminapi.inquiry.InquiryService;
import com.keaa.adminapi.mail.LeadMailer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * /api/catalogue-requests — the gate in front of the public Downloads Center.
 *
 * A visitor who wants a catalogue fills a short form; this stores the lead and only then does
 * the site hand over the file. The submission becomes a unified {@link Inquiry} of type
 * CATALOGUE, exactly like the RFQ and Contact forms do, so it inherits the whole pipeline for
 * free: auto-assignment by country and product line, the status workflow, the activity trail,
 * and the admin screens that already read /api/inquiries.
 *
 * The desk is also emailed (LeadMailer). A mail failure must never cost the lead: the record
 * is committed first and the notification is best-effort.
 */
@RestController
@RequestMapping("/api/catalogue-requests")
@RequiredArgsConstructor
public class CatalogueRequestController {

    private final InquiryService inquiryService;
    private final LeadMailer mailer;

    /** Public (no auth), like the other website forms. */
    @PostMapping
    public ResponseEntity<?> submit(@RequestBody CatalogueRequest req) {
        if (isBlank(req.name()) || isBlank(req.email())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Name and email are required."));
        }

        String catalogue = isBlank(req.catalogue()) ? "Catalogue" : req.catalogue().trim();

        Inquiry inq = inquiryService.create(Inquiry.builder()
                .type("CATALOGUE")
                .name(req.name().trim())
                .company(trim(req.company()))
                .email(req.email().trim())
                .phone(trim(req.phone()))
                .country(trim(req.country()))
                .category(trim(req.category()))
                .subject("Catalogue download: " + catalogue)
                .message("Requested \"" + catalogue + "\" from the Downloads Center.")
                .build());

        mailer.catalogueRequested(inq, catalogue);

        return ResponseEntity.ok(Map.of("id", inq.getId()));
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
    private static String trim(String s) { return s == null ? null : s.trim(); }

    /** `catalogue` is the title of the file the visitor asked for, e.g. "Livestock Housing Solutions Catalogue". */
    public record CatalogueRequest(String name, String company, String email, String phone,
                                   String country, String category, String catalogue) {}
}
