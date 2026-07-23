package com.keaa.adminapi.career;

import com.cloudinary.Cloudinary;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** /api/careers — job applications. */
@RestController
@RequestMapping("/api/careers")
@RequiredArgsConstructor
public class JobApplicationController {

    private final JobApplicationRepository jobApplicationRepository;
    private final Cloudinary cloudinary;

    // Resumes land here in the Cloudinary Media Library (FULL nested path, like products/gallery,
    // else Cloudinary makes a separate top-level folder of the same leaf name).
    @Value("${cloudinary.resume-folder:1.Keaa Assets/Keaa Resumes}")
    private String resumeFolder;

    @GetMapping
    public List<JobApplication> all() {
        return jobApplicationRepository.findAllByOrderByCreatedAtDesc();
    }

    /** Public: the website's careers form posts here when there is no CV (or as the JSON fallback). */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public JobApplication submit(@RequestBody JobApplication app) {
        return saveNew(app);
    }

    /**
     * Public: the careers form posts here WITH a CV as multipart/form-data — a JSON `payload`
     * part (the same body the JSON route takes) and a `resume` file part. The CV is uploaded to
     * Cloudinary and its URL stored on the application. This is what src/data/adminApi.js
     * (submitPublicFormWithFile) sends first; the JSON route above is only its fallback, which is
     * why a multipart POST used to 403 — there was no handler for it.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public JobApplication submitWithResume(
            @RequestPart("payload") JobApplication app,
            @RequestPart(value = "resume", required = false) MultipartFile resume) throws IOException {
        if (resume != null && !resume.isEmpty()) {
            app.setResumeUrl(uploadResume(resume));
        }
        return saveNew(app);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<JobApplication> updateStatus(@PathVariable Long id, @RequestBody StatusRequest body) {
        return jobApplicationRepository.findById(id).map(a -> {
            a.setStatus(body.status());
            return ResponseEntity.ok(jobApplicationRepository.save(a));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** A new application always starts fresh and "new", however it arrived. */
    private JobApplication saveNew(JobApplication app) {
        app.setId(null);
        app.setStatus("new");
        return jobApplicationRepository.save(app);
    }

    /**
     * Upload a CV to Cloudinary and return its https URL (stored in resumeUrl and linked from the
     * admin ATS). resource_type "auto" lets Cloudinary classify the file: a PDF becomes an IMAGE
     * asset, so the Media Library shows a real page preview and HR can view/download it as a PDF;
     * a Word doc (or anything else) is stored as RAW, untouched.
     *
     * NOTE: PDF delivery must be enabled on the Cloudinary account (Console → Settings → Security
     * → "Allow delivery of PDF and ZIP files"), otherwise every resume URL returns HTTP 401. It is
     * off by default.
     */
    private String uploadResume(MultipartFile file) throws IOException {
        Map<String, Object> options = new HashMap<>();
        options.put("folder", resumeFolder);
        options.put("resource_type", "auto");
        options.put("use_filename", true);
        options.put("unique_filename", true);
        Map<?, ?> res = cloudinary.uploader().upload(file.getBytes(), options);
        return String.valueOf(res.get("secure_url"));
    }

    public record StatusRequest(String status) {}
}
