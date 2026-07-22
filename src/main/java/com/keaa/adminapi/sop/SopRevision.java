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

    @Column(columnDefinition = "TEXT")
    private String purpose;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> checklist;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> workflow;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> important;

    /** Display name of whoever saved this version (matches the InquiryActivity.updatedBy idiom). */
    private String editedBy;

    @Column(updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
