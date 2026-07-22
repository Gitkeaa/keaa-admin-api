package com.keaa.adminapi.inquiry;

import com.keaa.adminapi.user.Role;
import com.keaa.adminapi.user.User;
import com.keaa.adminapi.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * /api/inquiries — the sales pipeline. Admin/Super Admin see and manage everything; a Sales or
 * Marketing member sees and manages ONLY the inquiries assigned to their territory. The public
 * site never calls this — leads arrive through RfqController/ContactController, which hand them
 * to InquiryService for auto-assignment.
 */
@RestController
@RequestMapping("/api/inquiries")
@RequiredArgsConstructor
public class InquiryController {

    private final InquiryRepository inquiryRepository;
    private final InquiryActivityRepository activityRepository;
    private final InquiryService inquiryService;
    private final UserRepository userRepository;

    private User me(Authentication auth) {
        return userRepository.findByEmail(auth.getName()).orElseThrow();
    }

    private boolean isManager(User u) {
        return u.getRole() == Role.SUPER_ADMIN || u.getRole() == Role.SENIOR_ADMIN || u.getRole() == Role.ADMIN;
    }

    /** Anyone allowed to VIEW an inquiry: managers see all, a rep sees their own. */
    private boolean canAccess(User u, Inquiry inq) {
        return isManager(u) || isAssignee(u, inq);
    }

    /** Only the assigned rep may CHANGE an inquiry (status / remarks). Managers are view-only. */
    private boolean isAssignee(User u, Inquiry inq) {
        return inq.getAssignedUserId() != null && inq.getAssignedUserId().equals(u.getId());
    }

    /** Managers get everything; a rep gets only what's assigned to them. Optional ?type filter. */
    @GetMapping
    public List<InquiryDto> list(Authentication auth, @RequestParam(required = false) String type) {
        User u = me(auth);
        List<Inquiry> all = isManager(u)
                ? inquiryRepository.findAllByOrderByCreatedAtDesc()
                : inquiryRepository.findByAssignedUserIdOrderByCreatedAtDesc(u.getId());
        return all.stream()
                .filter(i -> type == null || type.equalsIgnoreCase(i.getType()))
                .map(InquiryDto::from).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable Long id, Authentication auth) {
        User u = me(auth);
        Inquiry inq = inquiryRepository.findById(id).orElse(null);
        if (inq == null) return ResponseEntity.notFound().build();
        if (!canAccess(u, inq)) return ResponseEntity.status(403).build();
        List<ActivityDto> timeline = activityRepository.findByInquiryIdOrderByCreatedAtAsc(id)
                .stream().map(ActivityDto::from).toList();
        return ResponseEntity.ok(new DetailDto(InquiryDto.from(inq), timeline));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody StatusRequest req, Authentication auth) {
        User u = me(auth);
        Inquiry inq = inquiryRepository.findById(id).orElse(null);
        if (inq == null) return ResponseEntity.notFound().build();
        // Only the assigned rep can move an inquiry along. Managers (Admin/Super Admin) are view-only.
        if (!isAssignee(u, inq)) return ResponseEntity.status(403).body(err("Only the assigned member can update this inquiry."));
        if (!InquiryService.STATUSES.contains(req.status())) return ResponseEntity.badRequest().body(err("Unknown status."));
        if ("LOST".equals(req.status()) && (req.lostReason() == null || req.lostReason().isBlank())) {
            return ResponseEntity.badRequest().body(err("A reason is required to mark an inquiry as Lost."));
        }
        return ResponseEntity.ok(InquiryDto.from(inquiryService.updateStatus(inq, req.status(), req.remark(), req.lostReason(), u.getName())));
    }

    @PostMapping("/{id}/remark")
    public ResponseEntity<?> remark(@PathVariable Long id, @RequestBody RemarkRequest req, Authentication auth) {
        User u = me(auth);
        Inquiry inq = inquiryRepository.findById(id).orElse(null);
        if (inq == null) return ResponseEntity.notFound().build();
        if (!isAssignee(u, inq)) return ResponseEntity.status(403).body(err("Only the assigned member can update this inquiry."));
        if (req.remark() == null || req.remark().isBlank()) return ResponseEntity.badRequest().body(err("Remark is empty."));
        inquiryService.addRemark(id, req.remark(), u.getName());
        return ResponseEntity.ok().build();
    }

    /** Assigning / reassigning a lead is the ADMIN tier's job — Super/Senior Admin are view-only,
     *  and the Business Development rep only works what's assigned to them. */
    @PatchMapping("/{id}/assign")
    public ResponseEntity<?> assign(@PathVariable Long id, @RequestBody AssignRequest req, Authentication auth) {
        User u = me(auth);
        if (u.getRole() != Role.ADMIN) return ResponseEntity.status(403).body(err("Only an Admin can assign a lead."));
        Inquiry inq = inquiryRepository.findById(id).orElse(null);
        if (inq == null) return ResponseEntity.notFound().build();
        User target = req.userId() == null ? null : userRepository.findById(req.userId()).orElse(null);
        return ResponseEntity.ok(InquiryDto.from(inquiryService.assign(inq, target, u.getName())));
    }

    // ---- DTOs ----
    public record InquiryDto(Long id, String type, String name, String company, String email, String phone,
                             String country, String category, String subject, String message, String status,
                             Long assignedUserId, String assignedUserName, String lostReason,
                             Instant createdAt, Instant updatedAt) {
        static InquiryDto from(Inquiry i) {
            return new InquiryDto(i.getId(), i.getType(), i.getName(), i.getCompany(), i.getEmail(), i.getPhone(),
                    i.getCountry(), i.getCategory(), i.getSubject(), i.getMessage(), i.getStatus(),
                    i.getAssignedUserId(), i.getAssignedUserName(), i.getLostReason(), i.getCreatedAt(), i.getUpdatedAt());
        }
    }

    public record ActivityDto(String action, String fromStatus, String toStatus, String remark, String updatedBy, Instant at) {
        static ActivityDto from(InquiryActivity a) {
            return new ActivityDto(a.getAction(), a.getFromStatus(), a.getToStatus(), a.getRemark(), a.getUpdatedBy(), a.getCreatedAt());
        }
    }

    public record DetailDto(InquiryDto inquiry, List<ActivityDto> timeline) {}
    public record StatusRequest(String status, String remark, String lostReason) {}
    public record RemarkRequest(String remark) {}
    public record AssignRequest(Long userId) {}

    private static java.util.Map<String, String> err(String m) { return java.util.Map.of("error", m); }
}
