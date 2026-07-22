package com.keaa.adminapi.activity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * One entry in a user's own activity/login history — logins, password changes, profile
 * edits, "log out everywhere". Written by the app as those things happen and read back by the
 * Profile screen's Activity Timeline and Login History. Scoped to a user by id.
 */
@Entity
@Table(name = "activity_events", indexes = @Index(name = "idx_activity_user", columnList = "userId"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** LOGIN | PASSWORD_CHANGED | PROFILE_UPDATED | LOGOUT_ALL */
    @Column(nullable = false, length = 32)
    private String type;

    private String detail;

    /** Best-effort client info captured at the time (IP, user-agent summary). */
    private String ip;
    @Column(length = 512)
    private String userAgent;

    @Column(updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
