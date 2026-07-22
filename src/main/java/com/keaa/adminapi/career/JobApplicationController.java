package com.keaa.adminapi.career;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** /api/careers — job applications. */
@RestController
@RequestMapping("/api/careers")
@RequiredArgsConstructor
public class JobApplicationController {

    private final JobApplicationRepository jobApplicationRepository;

    @GetMapping
    public List<JobApplication> all() {
        return jobApplicationRepository.findAllByOrderByCreatedAtDesc();
    }

    /** Public: the website's careers form posts here (no auth). */
    @PostMapping
    public JobApplication submit(@RequestBody JobApplication app) {
        app.setId(null);
        app.setStatus("new");
        return jobApplicationRepository.save(app);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<JobApplication> updateStatus(@PathVariable Long id, @RequestBody StatusRequest body) {
        return jobApplicationRepository.findById(id).map(a -> {
            a.setStatus(body.status());
            return ResponseEntity.ok(jobApplicationRepository.save(a));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record StatusRequest(String status) {}
}
