package com.keaa.adminapi.dashboard;

import com.keaa.adminapi.career.JobApplicationRepository;
import com.keaa.adminapi.contact.ContactRepository;
import com.keaa.adminapi.feedback.FeedbackRepository;
import com.keaa.adminapi.product.ProductRepository;
import com.keaa.adminapi.rfq.RfqRepository;
import com.keaa.adminapi.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** /api/dashboard/summary — the KPI counts the dashboard tiles show. */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final RfqRepository rfqRepository;
    private final ContactRepository contactRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final FeedbackRepository feedbackRepository;

    @GetMapping("/summary")
    public Summary summary() {
        Double avg = feedbackRepository.averageRating();
        return new Summary(
                rfqRepository.countByStatus("new"),
                contactRepository.countByStatus("unread"),
                jobApplicationRepository.countByStatus("new"),
                productRepository.count(),
                userRepository.countByActiveTrue(),
                feedbackRepository.countByStatus("new"),
                // Rounded to one decimal here rather than in the browser, so every surface that
                // reads this number shows the same value. Stays null on an empty table.
                avg == null ? null : Math.round(avg * 10) / 10.0
        );
    }

    /** `avgRating` is null, NOT 0, when no feedback exists yet — the tile renders a dash instead
     *  of claiming a zero-star average nobody gave. */
    public record Summary(long openRfq, long unreadMessages, long newApplications, long products,
                          long activeUsers, long newFeedback, Double avgRating) {}
}
