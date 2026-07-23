package com.keaa.adminapi.sop;

import com.keaa.adminapi.user.User;
import com.keaa.adminapi.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Reads are open to any signed-in user (everyone needs their guides); writes are restricted to
 * Super Admin / Senior Admin by the URL matcher in SecurityConfig. Every save snapshots the
 * version it replaces into sop_revisions.
 */
@RestController
@RequestMapping("/api/sops")
@RequiredArgsConstructor
public class SopController {

    private final SopRepository sopRepository;
    private final SopRevisionRepository revisionRepository;
    private final UserRepository userRepository;

    /** The whole set, loaded once by the frontend; it picks the role/page rows it needs. */
    @GetMapping
    public List<Sop> list() {
        return sopRepository.findAll();
    }

    @GetMapping("/{id}/revisions")
    public List<SopRevision> revisions(@PathVariable Long id) {
        return revisionRepository.findBySopIdOrderByCreatedAtDesc(id);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody SopRequest body, Authentication auth) {
        return sopRepository.findById(id).map(sop -> {
            User editor = userRepository.findByEmail(auth.getName()).orElseThrow();

            // Snapshot the version we are about to overwrite, with who changed it and why. A
            // "restore" is just a normal save of an older snapshot's content, so it is captured
            // here too.
            revisionRepository.save(SopRevision.builder()
                    .sopId(sop.getId())
                    .title(sop.getTitle())
                    .department(sop.getDepartment())
                    .purpose(sop.getPurpose())
                    .workflow(sop.getWorkflow())
                    .responsibilities(sop.getResponsibilities())
                    .checklist(sop.getChecklist())
                    .bestPractices(sop.getBestPractices())
                    .important(sop.getImportant())
                    .quickTips(sop.getQuickTips())
                    .related(sop.getRelated())
                    .editedBy(editor.getName())
                    .changeSummary(body.changeSummary())
                    .build());

            sop.setTitle(body.title());
            sop.setDepartment(body.department());
            sop.setPurpose(body.purpose());
            sop.setWorkflow(body.workflow());
            sop.setResponsibilities(body.responsibilities());
            sop.setChecklist(body.checklist());
            sop.setBestPractices(body.bestPractices());
            sop.setImportant(body.important());
            sop.setQuickTips(body.quickTips());
            sop.setRelated(body.related());
            sop.setUpdatedBy(editor.getName());
            sop.setVersion((sop.getVersion() == null ? 1 : sop.getVersion()) + 1);
            return ResponseEntity.ok(sopRepository.save(sop));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ---- DTOs ----
    public record SopRequest(String title, String department, String purpose,
                             List<String> workflow, List<String> responsibilities,
                             List<String> checklist, List<String> bestPractices,
                             List<String> important, List<String> quickTips,
                             List<String> related, String changeSummary) {}
}
