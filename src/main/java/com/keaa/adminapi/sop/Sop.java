package com.keaa.adminapi.sop;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "sops",
        uniqueConstraints = @UniqueConstraint(name = "uq_sop_scope_ref", columnNames = {"scope", "refKey"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** "role" or "page". */
    @Column(nullable = false, length = 8)
    private String scope;

    /** scope="role" -> a Role name (BUSINESS_DEVELOPMENT); scope="page" -> a module key (rfq). */
    @Column(nullable = false, length = 48)
    private String refKey;

    @Column(nullable = false, length = 120)
    private String title;

    /** The owning function: Administration, Business Development, HR, Website, General. Drives the
     *  SOP manager's department filter and a chip in the help drawer header. */
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

    /** Related module keys, rendered as jump-to chips at the foot of the guide. */
    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> related;

    /** Display name of whoever last published this guide. */
    private String updatedBy;

    /** Bumps on every publish; 1 for a freshly seeded guide. Column renamed to avoid the MySQL
     *  reserved word VERSION; the JSON field stays "version". */
    @Column(name = "doc_version")
    private Integer version;

    @Column(updatable = false)
    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (version == null) version = 1;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
