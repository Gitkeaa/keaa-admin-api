package com.keaa.adminapi.contact;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** A message from the public site's contact form. */
@Entity
@Table(name = "contact_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContactMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String email;
    private String company;
    private String phone;
    private String subject;
    /** Country + product category let a contact message be auto-assigned like an RFQ. */
    private String country;
    private String category;

    @Column(columnDefinition = "TEXT")
    private String message;

    /** unread | read | replied */
    @Builder.Default
    @Column(length = 20)
    private String status = "unread";

    @Column(updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
