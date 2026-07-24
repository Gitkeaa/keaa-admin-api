package com.keaa.adminapi.feedback;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A star rating and message left through the feedback drawer on the public site.
 *
 * DELIBERATELY NOT AN {@link com.keaa.adminapi.inquiry.Inquiry}, and that is the whole design
 * decision here. Every other public form on the site (RFQ, Contact, Export, Catalogue) converges
 * on Inquiry, because each of those is a lead: InquiryService auto-assigns it to the rep whose
 * territory matches the country and product category, and it then carries the sales workflow
 * NEW → CONTACTED → QUOTATION_SENT → … → WON / LOST.
 *
 * Feedback fits none of that. It is usually anonymous, so there is no country or category to
 * assign on; it carries a numeric rating meant to be averaged, which no Inquiry field holds; and
 * most of it is never replied to, so "WON / LOST" is meaningless. Routing it through the pipeline
 * would drop unassignable rows into the Business Development queue and put a satisfaction score
 * behind a "Quotation Sent" button. Hence its own table and its own light status set.
 */
@Entity
@Table(name = "feedback")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 1 to 5 stars. The controller rejects anything outside that range, so this is never null. */
    @Column(nullable = false)
    private Integer rating;

    /** suggestion | feedback | issue | compliment */
    @Builder.Default
    @Column(length = 20, nullable = false)
    private String type = "feedback";

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    /** Both optional: feedback may be left entirely anonymously. */
    private String name;
    private String email;

    /**
     * The visitor's answer to "Would you like us to contact you?".
     *
     * This is a LEGAL constraint, not a display flag. The site's privacy policy tells visitors
     * their address is used "only to reply to this feedback", so a row with this false must not
     * be mailed, added to any list, or used for follow-up EVEN IF an address is present.
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean contactConsent = false;

    /** Path + query the feedback was sent from, e.g. "/products/couplers". Without it, a report
     *  like "the download link is broken" is unactionable. */
    @Column(length = 512)
    private String pageUrl;

    /** new | reviewed | actioned | closed */
    @Builder.Default
    @Column(length = 20)
    private String status = "new";

    @Column(updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
