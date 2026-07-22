package com.keaa.adminapi.rfq;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** A quotation request submitted from the public site's RFQ form. */
@Entity
@Table(name = "rfq_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RfqRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String company;
    private String email;
    private String phone;
    private String country;
    private String category;

    /**
     * Which tab of the public form produced this: "quote" (a normal RFQ) or "export" (an
     * export inquiry). The admin Export Inquiries screen filters on it. Defaults to "quote"
     * so older rows and any caller that omits it read as ordinary quote requests.
     */
    @Builder.Default
    @Column(length = 20)
    private String type = "quote";

    @Column(columnDefinition = "TEXT")
    private String message;

    /** new | in-review | quoted | closed */
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
