package com.keaa.adminapi.download;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A downloadable file offered on the public site — a catalogue, datasheet or certificate.
 * The file itself is written to local disk (see DownloadController + app.upload.dir); this
 * row holds its metadata and the on-disk name. The public Downloads / Certifications pages
 * currently list static entries; pointing them at this table is the follow-up.
 */
@Entity
@Table(name = "downloads")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DownloadItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    /** Catalogue | Certificate | Datasheet | Brochure … drives the filter on the public page. */
    private String category;

    /** The original file name, shown to the user and used for the download. */
    private String fileName;

    /** The unique name the file is stored under on disk (never shown). */
    private String storedName;

    private String contentType;
    private long size;

    @Column(updatable = false)
    private Instant uploadedAt;

    @PrePersist
    void onCreate() {
        if (uploadedAt == null) uploadedAt = Instant.now();
    }
}
