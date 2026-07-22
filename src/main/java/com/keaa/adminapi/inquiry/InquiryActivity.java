package com.keaa.adminapi.inquiry;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * One entry in an inquiry's audit trail: who did what, when, with remarks. Written on
 * creation, assignment, every status change and every note, so the timeline is complete.
 */
@Entity
@Table(name = "inquiry_activities", indexes = @Index(name = "idx_inqact_inquiry", columnList = "inquiryId"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InquiryActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long inquiryId;

    /** CREATED | ASSIGNED | STATUS_CHANGE | REMARK */
    @Column(nullable = false, length = 24)
    private String action;

    @Column(length = 24)
    private String fromStatus;
    @Column(length = 24)
    private String toStatus;

    @Column(columnDefinition = "TEXT")
    private String remark;

    /** Display name of who made the change (or "System" for auto-assignment). */
    private String updatedBy;

    @Column(updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
