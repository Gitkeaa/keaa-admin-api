package com.keaa.adminapi.career;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** A job application submitted from the public site's careers page. */
@Entity
@Table(name = "job_applications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String email;
    private String phone;

    /** the position applied for */
    private String position;

    private String experience;
    private String location;

    @Column(length = 1000)
    private String resumeUrl;

    /** Cover note + everything else the applicant entered (LinkedIn, qualification, …). */
    @Column(columnDefinition = "TEXT")
    private String notes;

    /** new | shortlisted | interview | rejected | hired */
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
