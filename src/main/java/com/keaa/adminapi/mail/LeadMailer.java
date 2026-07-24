package com.keaa.adminapi.mail;

import com.keaa.adminapi.inquiry.Inquiry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Emails the sales desk when a lead arrives.
 *
 * Two deliberate properties:
 *
 *  1. OPTIONAL. Spring only creates a JavaMailSender when `spring.mail.host` is set, so this
 *     takes an ObjectProvider rather than the sender itself. With no SMTP configured the app
 *     starts and every form still works — the notification is logged as skipped instead of
 *     bringing the endpoint down. That is what lets the feature ship before the client hands
 *     over mailbox credentials.
 *  2. BEST EFFORT. The lead is already committed to the database before this is called, and
 *     every failure here is caught. A bounced notification must never lose the lead, and the
 *     admin console remains the source of truth either way.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LeadMailer {

    private final ObjectProvider<JavaMailSender> senders;

    /** Where notifications go. Comma-separated for more than one recipient. */
    @Value("${app.mail.notify:}")
    private String notify;

    /** Envelope sender. Most providers demand this match the authenticated mailbox. */
    @Value("${app.mail.from:}")
    private String from;

    public void catalogueRequested(Inquiry lead, String catalogue) {
        String subject = "Catalogue download: " + catalogue + " — " + safe(lead.getName());
        String body = """
                A visitor requested a catalogue from the Downloads Center.

                Catalogue : %s
                Name      : %s
                Company   : %s
                Email     : %s
                Phone     : %s
                Country   : %s
                Interest  : %s

                Assigned to: %s
                Open it in the admin console under Catalogue Requests (inquiry #%s).
                """.formatted(
                catalogue,
                safe(lead.getName()), safe(lead.getCompany()), safe(lead.getEmail()),
                safe(lead.getPhone()), safe(lead.getCountry()), safe(lead.getCategory()),
                lead.getAssignedUserName() == null ? "unassigned" : lead.getAssignedUserName(),
                lead.getId());

        send(subject, body);
    }

    private void send(String subject, String body) {
        JavaMailSender sender = senders.getIfAvailable();
        if (sender == null || notify == null || notify.isBlank()) {
            log.info("[mail] not configured (spring.mail.host / app.mail.notify) — skipped: {}", subject);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(notify.split("\\s*,\\s*"));
            if (from != null && !from.isBlank()) msg.setFrom(from);
            msg.setSubject(subject);
            msg.setText(body);
            sender.send(msg);
            log.info("[mail] sent: {}", subject);
        } catch (Exception e) {
            // Never propagate: the lead is already stored and the console will show it.
            log.warn("[mail] could not send \"{}\": {}", subject, e.getMessage());
        }
    }

    private static String safe(String s) { return s == null || s.isBlank() ? "—" : s; }
}
