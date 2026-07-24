package com.keaa.adminapi.inquiry;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A unified customer inquiry — the ONE record every RFQ, export inquiry, Contact-Us message
 * and catalogue download becomes, so the whole team runs the same pipeline and assignment
 * logic no matter where the lead came in.
 *
 * `type` (RFQ | EXPORT | CONTACT | CATALOGUE) keeps the streams distinguishable for the
 * existing screens, while `status` drives the shared sales workflow:
 *   NEW → CONTACTED → QUOTATION_SENT → FOLLOW_UP → NEGOTIATION → WON / LOST → CLOSED
 *
 * On creation the inquiry is auto-assigned to the Sales/Marketing member whose territory
 * (country + product category) matches; only that member — plus Admin/Super Admin — may then
 * move it along. Every change is written to InquiryActivity for a full audit trail.
 */
@Entity
@Table(name = "inquiries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** RFQ | EXPORT | CONTACT | CATALOGUE */
    @Column(nullable = false, length = 16)
    private String type;

    private String name;
    private String company;
    private String email;
    private String phone;
    private String country;
    private String category;
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String message;

    /** NEW | CONTACTED | QUOTATION_SENT | FOLLOW_UP | NEGOTIATION | WON | LOST | CLOSED */
    @Builder.Default
    @Column(length = 24)
    private String status = "NEW";

    private Long assignedUserId;
    /** Denormalised so lists render the owner without a join. */
    private String assignedUserName;

    @Column(columnDefinition = "TEXT")
    private String lostReason;

    @Column(updatable = false)
    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
