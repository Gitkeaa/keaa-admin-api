package com.keaa.adminapi.translation;

import jakarta.persistence.*;
import lombok.*;

/**
 * A single translation override: the value to use for a locale key in a given language.
 *
 * The site ships its base strings in code (src/i18n/locales.js) and content translation is
 * being rolled out gradually; this table lets the team add/adjust translated strings without
 * a deploy. Merging these overrides into the site's locale lookup is the follow-up step.
 *
 * Column names are explicit because both "key" and "value" are reserved words in MySQL.
 */
@Entity
@Table(name = "translations",
        uniqueConstraints = @UniqueConstraint(name = "uk_key_lang", columnNames = {"translation_key", "lang_code"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Translation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The locale key, e.g. "nav.home". */
    @Column(name = "translation_key", nullable = false)
    private String transKey;

    /** BCP-47 language code, e.g. "nl", "de". */
    @Column(name = "lang_code", nullable = false, length = 8)
    private String langCode;

    @Column(name = "translation_value", columnDefinition = "TEXT")
    private String value;
}
