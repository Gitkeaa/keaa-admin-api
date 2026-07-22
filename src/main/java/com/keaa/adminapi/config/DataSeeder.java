package com.keaa.adminapi.config;

import com.keaa.adminapi.career.JobApplication;
import com.keaa.adminapi.career.JobApplicationRepository;
import com.keaa.adminapi.category.Category;
import com.keaa.adminapi.category.CategoryRepository;
import com.keaa.adminapi.contact.ContactMessage;
import com.keaa.adminapi.contact.ContactRepository;
import com.keaa.adminapi.gallery.GalleryItem;
import com.keaa.adminapi.gallery.GalleryRepository;
import com.keaa.adminapi.inquiry.Inquiry;
import com.keaa.adminapi.inquiry.InquiryRepository;
import com.keaa.adminapi.inquiry.InquiryService;
import com.keaa.adminapi.product.Product;
import com.keaa.adminapi.product.ProductRepository;
import com.keaa.adminapi.rfq.RfqRepository;
import com.keaa.adminapi.rfq.RfqRequest;
import com.keaa.adminapi.sop.Sop;
import com.keaa.adminapi.sop.SopRepository;
import com.keaa.adminapi.user.Role;
import com.keaa.adminapi.user.User;
import com.keaa.adminapi.user.UserRepository;
import com.keaa.adminapi.video.VideoItem;
import com.keaa.adminapi.video.VideoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds first-run data so you can log in and every screen shows something. The sample
 * business data (products, RFQ, contacts, applications) is gated on its table being empty, so
 * restarting never duplicates it. The staff LOGINS are seeded one-by-one (create-if-missing by
 * email), so a newly added role's account appears on the next start even though the earlier
 * accounts already exist — no table wipe needed. Existing rows are never overwritten.
 *
 * Seed logins (all @keaa-international.net):
 *   Super Admin →  admin@…       / admin123
 *   Admin       →  manager@…     / manager123
 *   Sales       →  sales@…       / sales123
 *   Marketing   →  marketing@…   / marketing123
 *   HR          →  hr@…          / hr123
 *   Employee    →  employee@…    / employee123
 */
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final GalleryRepository galleryRepository;
    private final InquiryRepository inquiryRepository;
    private final InquiryService inquiryService;
    private final RfqRepository rfqRepository;
    private final ContactRepository contactRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final VideoRepository videoRepository;
    private final SopRepository sopRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        // Role migration FIRST, as raw SQL, before any User row is read as an entity: the old
        // SALES and MARKETING roles were merged into BUSINESS_DEVELOPMENT, and reading a row whose
        // stored role no longer exists in the enum would blow up. Idempotent — 0 rows after the
        // first run. (SENIOR_ADMIN is brand new, so nothing needs remapping into it.)
        // Widen the role column first — 'BUSINESS_DEVELOPMENT' (20) / 'SENIOR_ADMIN' (12) are longer
        // than the old values, and Hibernate's ddl-auto=update does not reliably grow an existing
        // column. Then remap the merged roles. Both are idempotent.
        jdbcTemplate.execute("ALTER TABLE users MODIFY COLUMN role VARCHAR(32) NOT NULL");
        jdbcTemplate.update("UPDATE users SET role = 'BUSINESS_DEVELOPMENT' WHERE role IN ('SALES', 'MARKETING')");

        // One account per role. seedUser inserts a missing account and back-fills the HR/profile
        // fields on existing ones (so the profile screen has real data after the columns were
        // added), without ever duplicating or resetting a password.
        seedUser("Raveesh Moudgil", "admin@keaa-international.net", "admin123", Role.SUPER_ADMIN,
                "KEAA-001", "Management", "Managing Director", LocalDate.of(2003, 4, 1));
        seedUser("Vikram Sethi", "senioradmin@keaa-international.net", "senior123", Role.SENIOR_ADMIN,
                "KEAA-004", "Management", "Vice President", LocalDate.of(2008, 3, 12));
        seedUser("Amit Khanna", "manager@keaa-international.net", "manager123", Role.ADMIN,
                "KEAA-014", "Operations", "Operations Manager", LocalDate.of(2015, 8, 17));
        seedUser("Bhupesh Gautam", "sales@keaa-international.net", "sales123", Role.BUSINESS_DEVELOPMENT,
                "KEAA-032", "Business Development", "Business Development Executive", LocalDate.of(2018, 2, 5));
        seedUser("Neha Kapoor", "marketing@keaa-international.net", "marketing123", Role.BUSINESS_DEVELOPMENT,
                "KEAA-041", "Business Development", "Business Development Executive", LocalDate.of(2020, 6, 22));
        seedUser("Priya Sharma", "hr@keaa-international.net", "hr123", Role.HR,
                "KEAA-009", "Human Resources", "HR Manager", LocalDate.of(2012, 11, 3));
        seedUser("Rohan Das", "employee@keaa-international.net", "employee123", Role.EMPLOYEE,
                "KEAA-058", "Production", "Production Engineer", LocalDate.of(2022, 1, 10));

        // Starting territories so auto-assignment has something to match (only if not set yet).
        setTerritoryIfEmpty("sales@keaa-international.net",
                "UAE,Saudi Arabia,Qatar,Oman,India", "Scaffolding & Formworks,Safety Products");
        setTerritoryIfEmpty("marketing@keaa-international.net",
                "Netherlands,Germany,Poland", "Livestock Housing Solutions,Wood Connectors / Garden Hardware");

        if (inquiryRepository.count() == 0) {
            inquiryService.create(Inquiry.builder().type("RFQ").name("Ahmed Al Mansoori").company("Al Mansoori Group")
                    .email("ahmed@almansoori.ae").phone("+971 50 123 4567").country("UAE").category("Scaffolding & Formworks")
                    .message("Bulk quote for Cuplock scaffolding, ~5000 units for a Dubai high-rise.").build());
            inquiryService.create(Inquiry.builder().type("EXPORT").name("Klaus Meyer").company("Meyer Agrar GmbH")
                    .email("klaus@meyer-agrar.de").phone("+49 151 2345678").country("Germany").category("Livestock Housing Solutions")
                    .message("Export inquiry for cattle housing frames and feed barriers.").build());
            inquiryService.create(Inquiry.builder().type("CONTACT").name("Sara Kowalski").email("sara.k@polbud.pl")
                    .subject("Distributor partnership").country("Poland").category("Wood Connectors / Garden Hardware")
                    .message("Interested in distributing garden hardware across Poland.").build());
            inquiryService.create(Inquiry.builder().type("RFQ").name("Wei Chen").company("SinoBuild")
                    .email("wei.chen@sinobuild.cn").country("China").category("Formwork Accessories")
                    .message("Quote request — no territory owner yet (stays unassigned).").build());
        }

        // Load the real catalogue into the DB so the admin Products screen manages genuine data.
        // Runs only while the table is empty or still holds the tiny legacy demo set — never on a
        // catalogue an admin has already curated.
        if (productRepository.count() < 20) {
            List<Product> catalogue = readCatalogue();
            if (!catalogue.isEmpty()) {
                productRepository.deleteAll();
                productRepository.saveAll(catalogue);
            }
        }

        if (categoryRepository.count() == 0) {
            categoryRepository.save(Category.builder().name("Scaffolding & Formworks").slug("scaffolding-formworks")
                    .description("System scaffolds, shoring, formwork accessories and access solutions.").sortOrder(1).active(true).build());
            categoryRepository.save(Category.builder().name("Livestock Housing Solutions").slug("livestock-housing-solutions")
                    .description("Cattle housing frames, gates, feed barriers and dairy-unit hardware.").sortOrder(2).active(true).build());
            categoryRepository.save(Category.builder().name("Wood Connectors / Garden Hardware").slug("wood-connectors")
                    .description("Joist hangers, connectors and garden/timber hardware.").sortOrder(3).active(true).build());
            categoryRepository.save(Category.builder().name("Safety Products").slug("safety-products")
                    .description("Full body harnesses, helmets, nets and personal protective equipment.").sortOrder(4).active(true).build());
        }

        // Load the whole public gallery into the DB so the admin manages every photo the site
        // shows, not a handful. Runs only while the table is empty or still holds the tiny demo set.
        if (galleryRepository.count() < 20) {
            List<GalleryItem> gallery = readGalleryCatalogue();
            if (!gallery.isEmpty()) {
                galleryRepository.deleteAll();
                galleryRepository.saveAll(gallery);
            }
        }

        // The site's films (hero + project videos). Cloudinary video public_ids from data/content.js.
        if (videoRepository.count() == 0) {
            videoRepository.save(VideoItem.builder().cloudinaryId("hero1_a0hnen").title("KEAA International — Aerial Film").category("Hero").sortOrder(1).active(true).build());
            videoRepository.save(VideoItem.builder().cloudinaryId("Rass_wixfl0").title("Raas Industries — Film").category("Hero").sortOrder(2).active(true).build());
            videoRepository.save(VideoItem.builder().cloudinaryId("My_Video-highlight_sk4vj4").title("Highlights Film").category("Hero").sortOrder(3).active(true).build());
        }

        if (rfqRepository.count() == 0) {
            rfqRepository.save(RfqRequest.builder().name("Ahmed Al Mansoori").company("Al Mansoori Group")
                    .email("ahmed@almansoori.ae").country("UAE").category("Scaffolding & Formworks")
                    .message("Need a bulk quote for Cuplock scaffolding.").status("new").build());
            rfqRepository.save(RfqRequest.builder().name("Rajesh Kumar").company("BuildTech Constructors")
                    .email("rajesh@buildtech.in").country("India").category("Safety Products")
                    .message("Full body harnesses, quantity 200.").status("in-review").build());
        }

        if (contactRepository.count() == 0) {
            contactRepository.save(ContactMessage.builder().name("Wei Chen").email("wei.chen@sinobuild.cn")
                    .subject("Distributor partnership").message("We would like to distribute in China.").status("unread").build());
            contactRepository.save(ContactMessage.builder().name("Sara Kowalski").email("sara.k@polbud.pl")
                    .subject("Formwork technical specs").message("Please share EN specs.").status("read").build());
        }

        if (jobApplicationRepository.count() == 0) {
            jobApplicationRepository.save(JobApplication.builder().name("Neha Verma").email("neha.v@example.com")
                    .position("Production Engineer").experience("4 yrs").location("Ludhiana").status("new").build());
            jobApplicationRepository.save(JobApplication.builder().name("Rohit Singh").email("rohit.s@example.com")
                    .position("Export Sales Executive").experience("6 yrs").location("Remote").status("shortlisted").build());
        }

        if (sopRepository.count() == 0) {
            // ---- role SOPs (dashboard card + complete SOP) ----
            sopRepository.save(Sop.builder().scope("role").refKey("BUSINESS_DEVELOPMENT")
                    .title("Business Development SOP")
                    .purpose("Turn every assigned inquiry into a closed Won or Lost outcome.")
                    .checklist(List.of(
                            "Check new RFQs assigned to you",
                            "Contact the customer within 24 hours",
                            "Send the quotation",
                            "Keep every status updated",
                            "Close the inquiry as Won or Lost"))
                    .important(List.of(
                            "Do not skip a status - move through the workflow in order.",
                            "A Lost inquiry requires a reason.",
                            "Every action is logged."))
                    .build());

            sopRepository.save(Sop.builder().scope("role").refKey("HR")
                    .title("HR SOP")
                    .purpose("Move every applicant cleanly from application to offer or rejection.")
                    .checklist(List.of(
                            "Review new applications",
                            "Schedule interviews",
                            "Update each candidate's status",
                            "Upload interview feedback",
                            "Send the offer or the rejection"))
                    .important(List.of(
                            "Keep a candidate's status current - it is what the rest of the team sees.",
                            "Record a reason when you reject, for a fair and auditable trail."))
                    .build());

            sopRepository.save(Sop.builder().scope("role").refKey("EMPLOYEE")
                    .title("Employee SOP")
                    .purpose("Read-only access to the dashboard for visibility.")
                    .checklist(List.of(
                            "Review the dashboard overview",
                            "Raise anything that needs action with your desk lead"))
                    .important(List.of("Your access is read-only - you cannot change records."))
                    .build());

            // The three admin tiers share one SOP - seed a row per role so each is editable.
            for (String adminRole : List.of("SUPER_ADMIN", "SENIOR_ADMIN", "ADMIN")) {
                sopRepository.save(Sop.builder().scope("role").refKey(adminRole)
                        .title("Admin SOP")
                        .purpose("Oversee every desk and keep the site and its data healthy.")
                        .checklist(List.of(
                                "Review pending approvals and new inquiries",
                                "Manage employees and their assigned territories",
                                "Publish and update website content",
                                "Review reports across departments",
                                "Monitor that each desk is clearing its queue"))
                        .important(List.of(
                                "Only Super Admin and Senior Admin can change roles and permissions.",
                                "Every create, edit and delete is logged against your account."))
                        .build());
            }

            // ---- page guides (help drawer), keyed by the module key in roles.js ----
            sopRepository.save(Sop.builder().scope("page").refKey("rfq")
                    .title("RFQ Guide")
                    .purpose("Manage the customer RFQs assigned to you.")
                    .workflow(List.of("New", "Contacted", "Quotation Sent", "Negotiation", "Won / Lost", "Closed"))
                    .important(List.of("Do not skip a status.", "A Lost deal requires a reason.", "Every action is logged."))
                    .build());

            sopRepository.save(Sop.builder().scope("page").refKey("export-inquiries")
                    .title("Export Inquiries Guide")
                    .purpose("Handle export enquiries from overseas buyers, the same way as an RFQ.")
                    .workflow(List.of("New", "Contacted", "Quotation Sent", "Negotiation", "Won / Lost", "Closed"))
                    .important(List.of("Confirm the destination port and country before quoting.", "A Lost enquiry requires a reason."))
                    .build());

            sopRepository.save(Sop.builder().scope("page").refKey("contacts")
                    .title("Contact Messages Guide")
                    .purpose("Read and resolve general enquiries sent through the Contact page.")
                    .workflow(List.of("Unread", "Read", "Replied", "Closed"))
                    .important(List.of("Mark a message Read once you have opened it.",
                            "Convert a genuine buying enquiry into an RFQ rather than closing it here."))
                    .build());

            sopRepository.save(Sop.builder().scope("page").refKey("applications")
                    .title("Job Applications Guide")
                    .purpose("Move candidates from application through to a hiring decision.")
                    .workflow(List.of("New", "Shortlisted", "Interview", "Offer", "Hired / Rejected"))
                    .important(List.of("Keep each candidate's status current.", "Record a reason when rejecting."))
                    .build());

            sopRepository.save(Sop.builder().scope("page").refKey("users")
                    .title("User Management Guide")
                    .purpose("Create team members, set their role, and assign territory.")
                    .important(List.of(
                            "A user's role decides which modules they see - set it carefully.",
                            "Assigned countries and categories route inquiries to Business Development automatically.",
                            "Only Super Admin and Senior Admin can manage users."))
                    .build());

            sopRepository.save(Sop.builder().scope("page").refKey("products")
                    .title("Products Guide")
                    .purpose("Keep the public catalogue accurate - add, edit and organise products.")
                    .important(List.of("Item codes are shown to customers - keep them correct.",
                            "Business Development has read-only access here."))
                    .build());

            sopRepository.save(Sop.builder().scope("page").refKey("roles")
                    .title("Roles & Permissions Guide")
                    .purpose("Define what each role can see and do across the panel.")
                    .important(List.of("A permission change takes effect the next time that user signs in.",
                            "Only Super Admin and Senior Admin can reach this screen."))
                    .build());
        }
    }

    /**
     * Inserts a staff login if the email is new, otherwise back-fills only the HR/profile fields
     * that are still blank. Passwords, role and status of an existing account are never touched.
     */
    private void seedUser(String name, String email, String rawPassword, Role role,
                          String employeeId, String department, String designation, LocalDate joiningDate) {
        User u = userRepository.findByEmail(email).orElse(null);
        if (u == null) {
            userRepository.save(User.builder()
                    .name(name).email(email)
                    .password(passwordEncoder.encode(rawPassword)).role(role).active(true)
                    .employeeId(employeeId).department(department).designation(designation)
                    .joiningDate(joiningDate).createdBy("System").build());
            return;
        }
        boolean changed = false;
        if (u.getEmployeeId() == null) { u.setEmployeeId(employeeId); changed = true; }
        if (u.getDepartment() == null) { u.setDepartment(department); changed = true; }
        if (u.getDesignation() == null) { u.setDesignation(designation); changed = true; }
        if (u.getJoiningDate() == null) { u.setJoiningDate(joiningDate); changed = true; }
        if (u.getCreatedBy() == null) { u.setCreatedBy("System"); changed = true; }
        // A null theme means the account predates the preference columns — apply defaults once
        // (notifications on) so the profile screen opens in a sensible state.
        if (u.getTheme() == null) {
            u.setTheme("light");
            u.setLanguage("en");
            u.setTimezone("Asia/Kolkata");
            u.setDateFormat("DD MMM YYYY");
            u.setNotifyEmail(true);
            u.setNotifyRfq(true);
            u.setNotifyJobs(true);
            u.setNotifyContact(true);
            u.setNotifySecurity(true);
            changed = true;
        }
        if (changed) userRepository.save(u);
    }

    /** Sets a rep's territory only when it is still blank — never overwrites an admin's choice. */
    private void setTerritoryIfEmpty(String email, String countries, String categories) {
        userRepository.findByEmail(email).ifPresent(u -> {
            boolean changed = false;
            if (u.getAssignedCountries() == null || u.getAssignedCountries().isBlank()) { u.setAssignedCountries(countries); changed = true; }
            if (u.getAssignedCategories() == null || u.getAssignedCategories().isBlank()) { u.setAssignedCategories(categories); changed = true; }
            if (changed) userRepository.save(u);
        });
    }

    /** Reads the bundled gallery seed (gallery-seed.json) into GalleryItem rows. Empty if absent. */
    private List<GalleryItem> readGalleryCatalogue() throws Exception {
        ClassPathResource res = new ClassPathResource("gallery-seed.json");
        if (!res.exists()) return List.of();
        JsonNode arr = new ObjectMapper().readTree(res.getInputStream());
        List<GalleryItem> out = new ArrayList<>();
        for (JsonNode n : arr) {
            out.add(GalleryItem.builder()
                    .cloudinaryId(txt(n, "cloudinaryId"))
                    .category(txt(n, "category"))
                    .alt(txt(n, "alt"))
                    .sortOrder(n.path("sortOrder").asInt(0))
                    .active(true)
                    .build());
        }
        return out;
    }

    /** Reads the bundled catalogue (products-catalogue.json) into Product rows. Empty if absent. */
    private List<Product> readCatalogue() throws Exception {
        ClassPathResource res = new ClassPathResource("products-catalogue.json");
        if (!res.exists()) return List.of();
        JsonNode arr = new ObjectMapper().readTree(res.getInputStream());
        List<Product> out = new ArrayList<>();
        for (JsonNode n : arr) {
            JsonNode imgs = n.get("cloudinaryImages");
            String imageUrl = (imgs != null && imgs.isArray() && imgs.size() > 0) ? imgs.get(0).asText() : null;
            out.add(Product.builder()
                    .itemCode(txt(n, "itemCode"))
                    .name(txt(n, "name"))
                    .category(txt(n, "category"))
                    .subcategory(txt(n, "subcategory"))
                    .description(txt(n, "description"))
                    .imageUrl(imageUrl)
                    .build());
        }
        return out;
    }

    private static String txt(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }
}
