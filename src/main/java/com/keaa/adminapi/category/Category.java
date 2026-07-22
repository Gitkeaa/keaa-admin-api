package com.keaa.adminapi.category;

import jakarta.persistence.*;
import lombok.*;

/**
 * A top-level product category (Scaffolding & Formworks, Livestock Housing, …).
 *
 * On the public site these are currently derived from the catalogue (categories.json, built
 * from products.json). This table lets the catalogue owners edit the presentation — display
 * name, URL slug, blurb, order and whether it shows — server-side; wiring the public nav to
 * read from here is the follow-up step.
 */
@Entity
@Table(name = "product_categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** URL slug, e.g. "scaffolding-formworks". Unique so two categories can't share a route. */
    @Column(nullable = false, unique = true)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    private int sortOrder = 0;

    @Builder.Default
    private boolean active = true;
}
