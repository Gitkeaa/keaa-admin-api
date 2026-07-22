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
