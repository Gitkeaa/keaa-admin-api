package com.keaa.adminapi.settings;

import jakarta.persistence.*;
import lombok.*;

/**
 * The site's editable global settings — one single row (id = 1). These are the values the
 * public site currently hard-codes in data/company.js (contact details, social links, the
 * company blurb); holding them here lets the Super Admin change them without a code deploy.
 *
 * Multi-value fields (phones, landlines, emails) are stored as newline-separated text so the
 * shape stays simple; the admin form edits them as a small textarea and the public site can
 * split on newlines when it is wired to read from here.
 */
@Entity
@Table(name = "site_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SiteSettings {

    @Id
    private Long id; // always 1 — this table holds a single settings row

    private String companyName;
    private String tagline;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String addressLine1;
    private String addressLine2;

    @Column(columnDefinition = "TEXT")
    private String phones;    // one per line

    @Column(columnDefinition = "TEXT")
    private String landlines; // one per line

    @Column(columnDefinition = "TEXT")
    private String emails;    // one per line

    private String fax;

    private String linkedin;
    private String facebook;
    private String instagram;
    private String youtube;
    private String whatsapp;
    private String x;
}
