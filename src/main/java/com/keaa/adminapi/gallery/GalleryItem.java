package com.keaa.adminapi.gallery;

import jakarta.persistence.*;
import lombok.*;

/**
 * One image on the public Projects & Gallery page. The picture itself lives on Cloudinary
 * (the site delivers everything through it); here we store only its public_id plus the
 * category that drives the gallery filter, so the marketing team can curate the set without
 * touching code. Wiring the public gallery to read from this table is the follow-up.
 */
@Entity
@Table(name = "gallery_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GalleryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Cloudinary public_id, e.g. "DJI_0082_p2qmld". */
    @Column(nullable = false)
    private String cloudinaryId;

    private String category;

    /** Optional descriptive alt text for accessibility. */
    private String alt;

    @Builder.Default
    private int sortOrder = 0;

    @Builder.Default
    private boolean active = true;
}
