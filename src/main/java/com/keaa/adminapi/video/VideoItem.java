package com.keaa.adminapi.video;

import jakarta.persistence.*;
import lombok.*;

/**
 * One video shown on the public site (hero films / project films). The file lives on Cloudinary
 * (video/upload); here we store its public_id plus a title and category so the team can curate
 * the set without touching code.
 */
@Entity
@Table(name = "video_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Cloudinary VIDEO public_id, e.g. "hero1_a0hnen". */
    @Column(nullable = false)
    private String cloudinaryId;

    private String title;

    private String category;

    @Builder.Default
    private int sortOrder = 0;

    @Builder.Default
    private boolean active = true;
}
