package com.keaa.adminapi.user;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * An admin-console user. `password` holds a BCrypt hash, never plaintext. The table is
 * created automatically by Hibernate (ddl-auto=update) as `users`.
 *
 * Beyond the login essentials this also carries the profile/HR fields and per-user
 * preferences the "My Profile" screen reads and writes. New columns are nullable/defaulted so
 * ddl-auto=update adds them to existing rows without a migration.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role;

    @Builder.Default
    private boolean active = true;

    private Instant lastActive;

    @Column(updatable = false)
    private Instant createdAt;

    // ---- Personal / HR profile ----
    private String phone;
    private String department;
    private String designation;
    private String employeeId;
    private LocalDate joiningDate;
    @Column(length = 512)
    private String avatarUrl;

    // ---- Sales/Marketing territory (drives automatic inquiry assignment) ----
    /** Comma-joined country names this member owns. */
    @Column(columnDefinition = "TEXT")
    private String assignedCountries;
    /** Comma-joined product-category names this member owns. */
    @Column(columnDefinition = "TEXT")
    private String assignedCategories;

    // ---- Account meta ----
    private Instant lastPasswordChangedAt;
    private String createdBy;

    /**
     * Bumped by "log out of all devices". The value is embedded in each JWT; JwtAuthFilter
     * rejects a token whose version is stale, so every previously-issued token dies at once.
     */
    @Builder.Default
    private int tokenVersion = 0;

    @Builder.Default
    private boolean twoFactorEnabled = false;
    /** Base32 TOTP secret (set once 2FA is confirmed). */
    private String twoFactorSecret;
    private Instant lastTwoFactorAt;
    /** BCrypt-hashed recovery codes, comma-joined; one is removed as it is used. */
    @Column(columnDefinition = "TEXT")
    private String twoFactorBackupCodes;

    // ---- Preferences ----
    @Builder.Default
    @Column(length = 10)
    private String theme = "light";     // light | dark | system
    @Builder.Default
    @Column(length = 10)
    private String language = "en";
    @Builder.Default
    private String timezone = "Asia/Kolkata";
    @Builder.Default
    @Column(length = 20)
    private String dateFormat = "DD MMM YYYY";

    // ---- Notification preferences ----
    @Builder.Default
    private boolean notifyEmail = true;
    @Builder.Default
    private boolean notifyRfq = true;
    @Builder.Default
    private boolean notifyJobs = true;
    @Builder.Default
    private boolean notifyContact = true;
    @Builder.Default
    private boolean notifySecurity = true;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
