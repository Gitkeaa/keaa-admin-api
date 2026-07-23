package com.keaa.adminapi.sop;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "sop_revisions", indexes = @Index(name = "idx_soprev_sop", columnList = "sopId"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SopRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sopId;

    @Column(length = 120)
    private String title;

    @Column(length = 48)
    private String department;

    @Column(columnDefinition = "TEXT")
    private String purpose;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> workflow;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> responsibilities;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> checklist;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> bestPractices;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> important;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> quickTips;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> related;

    /** Display name of whoever saved this version (matches the InquiryActivity.updatedBy idiom). */
    private String editedBy;

    /** Short note describing the edit that replaced this snapshot. */
    @Column(columnDefinition = "TEXT")
    private String changeSummary;

    @Column(updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
