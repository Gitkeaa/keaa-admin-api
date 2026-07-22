package com.keaa.adminapi.dashboard;

import com.keaa.adminapi.career.JobApplicationRepository;
import com.keaa.adminapi.contact.ContactRepository;
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

    @GetMapping("/summary")
    public Summary summary() {
        return new Summary(
                rfqRepository.countByStatus("new"),
                contactRepository.countByStatus("unread"),
                jobApplicationRepository.countByStatus("new"),
                productRepository.count(),
                userRepository.countByActiveTrue()
        );
    }

    public record Summary(long openRfq, long unreadMessages, long newApplications, long products, long activeUsers) {}
}
