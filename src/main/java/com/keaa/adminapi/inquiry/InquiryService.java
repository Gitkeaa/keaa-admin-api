package com.keaa.adminapi.inquiry;

import com.keaa.adminapi.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Creates, auto-assigns and moves inquiries through the pipeline, writing the audit trail. */
@Service
@RequiredArgsConstructor
public class InquiryService {

    public static final List<String> STATUSES =
            List.of("NEW", "CONTACTED", "QUOTATION_SENT", "FOLLOW_UP", "NEGOTIATION", "WON", "LOST", "CLOSED");

    private final InquiryRepository inquiryRepository;
    private final InquiryActivityRepository activityRepository;
    private final AssignmentService assignmentService;

    /** Store a fresh inquiry, auto-assign it by territory, and log creation + assignment. */
    @Transactional
    public Inquiry create(Inquiry in) {
        in.setId(null);
        in.setStatus("NEW");
        User assignee = assignmentService.findAssignee(in.getCountry(), in.getCategory());
        if (assignee != null) {
            in.setAssignedUserId(assignee.getId());
            in.setAssignedUserName(assignee.getName());
        }
        Inquiry saved = inquiryRepository.save(in);
        log(saved.getId(), "CREATED", null, "NEW", "Inquiry received", "System");
        if (assignee != null) {
            log(saved.getId(), "ASSIGNED", null, null, "Auto-assigned to " + assignee.getName() + " (territory match)", "System");
        }
        return saved;
    }

    @Transactional
    public Inquiry updateStatus(Inquiry inq, String newStatus, String remark, String lostReason, String updatedBy) {
        String from = inq.getStatus();
        if ("LOST".equals(newStatus)) inq.setLostReason(lostReason);
        inq.setStatus(newStatus);
        Inquiry saved = inquiryRepository.save(inq);
        String note = remark;
        if ("LOST".equals(newStatus) && lostReason != null && !lostReason.isBlank()) {
            note = (remark == null || remark.isBlank() ? "" : remark + " — ") + "Lost reason: " + lostReason;
        }
        log(saved.getId(), "STATUS_CHANGE", from, newStatus, note, updatedBy);
        return saved;
    }

    @Transactional
    public void addRemark(Long inquiryId, String remark, String updatedBy) {
        log(inquiryId, "REMARK", null, null, remark, updatedBy);
    }

    /** Admin re-route to a specific member. */
    @Transactional
    public Inquiry assign(Inquiry inq, User assignee, String updatedBy) {
        inq.setAssignedUserId(assignee == null ? null : assignee.getId());
        inq.setAssignedUserName(assignee == null ? null : assignee.getName());
        Inquiry saved = inquiryRepository.save(inq);
        log(saved.getId(), "ASSIGNED", null, null,
                assignee == null ? "Unassigned" : "Reassigned to " + assignee.getName(), updatedBy);
        return saved;
    }

    private void log(Long inquiryId, String action, String from, String to, String remark, String by) {
        activityRepository.save(InquiryActivity.builder()
                .inquiryId(inquiryId).action(action).fromStatus(from).toStatus(to).remark(remark).updatedBy(by).build());
    }
}
