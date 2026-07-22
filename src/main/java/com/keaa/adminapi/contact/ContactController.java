package com.keaa.adminapi.contact;

import com.keaa.adminapi.inquiry.Inquiry;
import com.keaa.adminapi.inquiry.InquiryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** /api/contact — the website's contact form. A submission becomes a unified, auto-assigned
 *  Inquiry (type CONTACT); the old GET/PATCH stay for any legacy rows. */
@RestController
@RequestMapping("/api/contact")
@RequiredArgsConstructor
public class ContactController {

    private final ContactRepository contactRepository;
    private final InquiryService inquiryService;

    @GetMapping
    public List<ContactMessage> all() {
        return contactRepository.findAllByOrderByCreatedAtDesc();
    }

    /** Public (no auth): create a CONTACT inquiry from the contact form. */
    @PostMapping
    public ResponseEntity<?> submit(@RequestBody ContactMessage msg) {
        Inquiry inq = inquiryService.create(Inquiry.builder()
                .type("CONTACT")
                .name(msg.getName()).email(msg.getEmail()).company(msg.getCompany()).phone(msg.getPhone())
                .subject(msg.getSubject())
                .country(msg.getCountry()).category(msg.getCategory()).message(msg.getMessage())
                .build());
        return ResponseEntity.ok(Map.of("id", inq.getId()));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ContactMessage> updateStatus(@PathVariable Long id, @RequestBody StatusRequest body) {
        return contactRepository.findById(id).map(m -> {
            m.setStatus(body.status());
            return ResponseEntity.ok(contactRepository.save(m));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record StatusRequest(String status) {}
}
