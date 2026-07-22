package com.keaa.adminapi.rfq;

import com.keaa.adminapi.inquiry.Inquiry;
import com.keaa.adminapi.inquiry.InquiryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * /api/rfq — the website's RFQ / export-inquiry form still POSTs here, but a submission now
 * becomes a unified Inquiry (auto-assigned to the owning Sales/Marketing rep) rather than a
 * stand-alone RFQ row. The old GET/PATCH remain for any legacy data.
 */
@RestController
@RequestMapping("/api/rfq")
@RequiredArgsConstructor
public class RfqController {

    private final RfqRepository rfqRepository;
    private final InquiryService inquiryService;

    @GetMapping
    public List<RfqRequest> all() {
        return rfqRepository.findAllByOrderByCreatedAtDesc();
    }

    /** Public (no auth): create an inquiry from the RFQ form. The Export tab sets type=export. */
    @PostMapping
    public ResponseEntity<?> submit(@RequestBody RfqRequest req) {
        String type = "export".equalsIgnoreCase(req.getType()) ? "EXPORT" : "RFQ";
        Inquiry inq = inquiryService.create(Inquiry.builder()
                .type(type)
                .name(req.getName()).company(req.getCompany()).email(req.getEmail()).phone(req.getPhone())
                .country(req.getCountry()).category(req.getCategory()).message(req.getMessage())
                .build());
        return ResponseEntity.ok(Map.of("id", inq.getId()));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<RfqRequest> updateStatus(@PathVariable Long id, @RequestBody StatusRequest body) {
        return rfqRepository.findById(id).map(r -> {
            r.setStatus(body.status());
            return ResponseEntity.ok(rfqRepository.save(r));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record StatusRequest(String status) {}
}
