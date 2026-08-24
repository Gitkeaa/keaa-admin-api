package com.keaa.adminapi.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/** Builds the assistant's knowledge base from the website's own content.
 *
 *  The source is knowledge-base-data.json, exported from the React app's src/data files by
 *  `npm run export:chat-kb` in the frontend repo. Re-run that whenever site content changes,
 *  drop the new file in resources, restart, and the assistant answers from the new content.
 *
 *  Two forms are produced and both are needed. {@code fullInstruction} is the text prompt
 *  Claude receives. {@code data} is the same content still structured, which
 *  {@link OfflineAnswerService} searches when the Claude API cannot be reached. */
@Service
@Slf4j
public class KnowledgeBaseService {

    private static final String SYSTEM_PROMPT = """
            You are the KEAA AI Assistant, a helpful and professional assistant for KEAA International Pvt. Ltd. \
            — an Indo-Dutch company that manufactures and exports scaffolding systems, formwork accessories, \
            safety products, livestock housing solutions and garden hardware.

            Company Information:
            - Name: KEAA International Pvt. Ltd. (short: KEAA)
            - Tagline: "Built for Safety. Built to Last."
            - Founded: 2003
            - Manufacturing plant: Village Bhagwanpura, Dehlon Road, Ludhiana – 141120, Punjab, India (25,000 sq. m in-house facilities)
            - European sales office & warehouse: Park Forum 1005, 5657 HJ Eindhoven, The Netherlands
            - Main products: Scaffolding systems, formwork accessories, safety products, livestock housing solutions, garden hardware
            - Reach: Exports to 42+ countries with 20+ years of experience and 150+ skilled employees

            Capabilities & Quality:
            - In-house hot dip galvanizing (4 m and 1.7 m zinc baths, DIN EN 1461), automatic powder coating, sheet & tube laser cutting, robotic welding, CNC press brake
            - Certified welders per EN 1090-2 / EN ISO 3834-2 (SLV Germany); Ü-mark props (EN 1065 Class BD) and couplers (EN 74-1 B/BB) via Sigma Karlsruhe
            - Certifications: ISO 9001:2015 (Quality Management), ISO 14001:2015 (Environmental Management) and ISO 45001:2018 (Occupational Health & Safety) — all certified by TÜV Rheinland; plus ZED Silver (MSME Sustainable / Zero Defect Zero Effect, Govt. of India)

            Contact Information:
            - India (manufacturing): +91 98767 01926, +91 98729 84707 — emails: raveesh@keaa-international.net, bhupesh@keaa-international.net, sumit@keaa-international.net
            - Netherlands (sales): +31 655 282 244
            - Website: www.keaainternational.com

            Guidelines:
            1. Answer questions about KEAA's products, services, manufacturing capabilities and company information.
            2. Be professional, concise and courteous. Keep answers short unless the user asks for detail.
            3. For quotations, bulk/OEM orders or export inquiries, guide users to the "Request a Quote" (RFQ) page or the contact details above.
            4. If you don't know something specific, say so honestly and suggest contacting the company directly.
            5. Only discuss KEAA and its offerings; politely decline unrelated requests.
            6. The KNOWLEDGE BASE below is your ONLY source of truth for products, categories, item codes, sizes, specs, projects, certifications, careers and resources. Every product name, category, item code, dimension, finish, spec and link you give MUST appear in it verbatim — never invent, guess, approximate or round a value. We have exactly 3 product categories and 355 catalogued products; do not claim any others. When you name a product, link its exact product-page path from the knowledge base (e.g. [Cuplock Standard](/product/13)); when you name a category or subcategory, link its "page:" path. If a detail (a price, a spec, a product) is not in the knowledge base, say you don't have it and point the customer to the RFQ form or the contact details — do not fabricate it.
            7. Use the KEY PAGES list to guide visitors: when a whole page answers them, link it by its exact path, e.g. [Manufacturing](/manufacturing), [Certifications](/certifications), [Downloads Center](/downloads) or [Contact Us](/contact). Send every pricing, minimum-order, lead-time, sample or OEM/custom question to the Request a Quote page ([RFQ](/contact?tab=rfq)), and never state or estimate a figure that is not in the knowledge base. The FAQ, COMPANY PROFILE and OFFICES & CONTACT sections are authoritative for ordering, manufacturing, export, compliance and contact questions.
            8. FORMATTING: Reply in clean, well-structured Markdown so it is easy to scan. Never use an em dash (—) anywhere in your reply; use a comma, colon or a shorter sentence instead. Start with a one-line summary sentence. For any list of 3+ items, use hyphen "-" bullet points (each on its own line), and put a blank line before the list. Use **bold** only for key terms or category names. Keep paragraphs to 1-2 sentences. Never cram a list into a single paragraph.""";

    /** The site's main pages, so the assistant can point a visitor at the right one.
     *  RFQ is a tab on the contact page, not a route of its own. */
    private static final String[][] KEY_PAGES = {
            {"/", "Home"},
            {"/about", "About Us: company story, journey, leadership and team"},
            {"/products", "All Products: every line we manufacture"},
            {"/manufacturing", "Manufacturing: facilities, the 7-step process, machinery and output at scale"},
            {"/projects-gallery", "Projects & Gallery: completed projects, factory photography and films"},
            {"/certifications", "Certifications: ISO, EN 1090, SLV welding, Ü-mark and test reports"},
            {"/downloads", "Downloads Center: the full product catalogues (PDF)"},
            {"/faq", "FAQ & Testimonials: ordering, finishes, lead times, export and customer reviews"},
            {"/careers", "Careers: current openings"},
            {"/contact?tab=rfq", "Request a Quote (RFQ): the place for prices, minimum order, lead times, OEM and custom orders"},
            {"/contact", "Contact Us: offices, phone and email"},
            {"/privacy-policy", "Privacy Policy"},
            {"/terms", "Terms of Use"},
            {"/cookie-policy", "Cookie Policy"},
    };

    private final ObjectMapper mapper = new ObjectMapper();

    /** The structured content, for the offline fallback's keyword search. Null if loading failed. */
    @Getter
    private JsonNode data;

    /** System prompt + rendered knowledge base: everything Claude is told before the question. */
    @Getter
    private String fullInstruction = SYSTEM_PROMPT;

    @PostConstruct
    void load() {
        try (InputStream in = new ClassPathResource("knowledge-base-data.json").getInputStream()) {
            this.data = mapper.readTree(in);
            this.fullInstruction = SYSTEM_PROMPT + "\n" + buildKnowledgeBase(this.data);
            log.info("[chat] knowledge base loaded: {} chars", fullInstruction.length());
        } catch (Exception e) {
            // Never fail startup over the knowledge base: the assistant can still answer from
            // the base prompt, and the log says exactly what is missing.
            log.error("[chat] could not load knowledge base, running on the base prompt only: {}", e.getMessage());
        }
    }

    public boolean isLoaded() {
        return data != null;
    }

    // ------------------------------------------------------------------ rendering

    private String buildKnowledgeBase(JsonNode d) {
        JsonNode company = d.path("company");
        JsonNode catalogue = d.path("catalogue");
        JsonNode categoryTree = d.path("categoryTree");

        StringBuilder sb = new StringBuilder(300_000);
        sb.append("""

                === KEAA KNOWLEDGE BASE (authoritative — answer from this) ===

                HOW TO USE THE TWO PRODUCT SECTIONS BELOW:
                - BROWSABLE CATALOGUE is every product with its own page on the website. When a customer
                  asks what we make, or about a specific product, answer from here. Its names, item codes,
                  descriptions and specs are authoritative — quote them exactly, never paraphrase a number.
                - Each product line ends with its page in parentheses, e.g. (/product/16). Link a product
                  as a Markdown link: [Ringlock Standard](/product/13). Link a whole category or a
                  subcategory using its "page:" path. Use these paths verbatim; never invent or alter one.
                - ITEM CODE & SIZE REFERENCE comes from KEAA's printed catalogues. Use it when a customer
                  asks for an item code or a size range. Some of these have no page on the website.
                - Safety Products is real and we sell it, but it has no catalogue page yet. Never claim we
                  do not make it, and never link to a product page for it — route the customer to the RFQ.

                """);

        sb.append("BROWSABLE CATALOGUE (").append(catalogue.size()).append(" products across ")
                .append(categoryTree.size()).append(" categories — every one has its own page on the website).\n")
                .append("""
                        Line format: ITEM CODE — Name (product page path) | description | specs. Category and
                        subcategory headers carry their own "page:" path. Link products/categories using these
                        exact paths; state only item codes, sizes, finishes and specs that appear here.
                        """)
                .append(renderCatalogue(catalogue, categoryTree)).append("\n\n");

        sb.append("ITEM CODE & SIZE REFERENCE (from KEAA's printed catalogues):\n")
                .append(renderItemCodes(d.path("productCategories"), d.path("enquiryOnlySlugs"))).append("\n\n");

        sb.append("BEST SELLERS:\n");
        for (JsonNode b : arr(d.path("bestSellers"))) {
            sb.append("- ").append(b.path("name").asText()).append(" (").append(b.path("category").asText()).append(")\n");
        }

        sb.append("\nCERTIFICATIONS:\n");
        for (JsonNode c : arr(company.path("certifications"))) {
            sb.append("- ").append(c.path("name").asText()).append(" (").append(c.path("body").asText())
                    .append("): ").append(c.path("note").asText()).append("\n");
        }

        sb.append(renderCompanyProfile(company));
        sb.append(renderContact(company));

        sb.append("\n\nFEATURED PROJECTS:\n");
        for (JsonNode p : arr(d.path("featuredProjects"))) {
            sb.append("- ").append(p.path("title").asText()).append(" — ").append(p.path("location").asText())
                    .append(" (").append(p.path("category").asText()).append("): ").append(p.path("desc").asText()).append("\n");
        }

        sb.append("\nEXPORT COUNTRIES: ")
                .append(StreamSupport.stream(arr(d.path("countries")).spliterator(), false)
                        .map(c -> c.path("name").asText()).collect(Collectors.joining(", ")))
                .append("\n");

        sb.append("\nLEADERSHIP & MANAGEMENT (KEAA \"About Us\" — these are the real people; use their names, "
                + "titles and quoted messages exactly, and never invent a person, title or quote):\n");
        JsonNode chairman = d.path("chairman");
        if (chairman.isObject() && !chairman.isEmpty()) {
            sb.append("Chairman:\n- ").append(chairman.path("name").asText()).append(" — ")
                    .append(chairman.path("role").asText()).append(". Message: \"")
                    .append(chairman.path("message").asText()).append("\"\n");
        }
        sb.append("Managing Directors:\n");
        for (JsonNode m : arr(d.path("managingDirectors"))) {
            sb.append("- ").append(m.path("name").asText()).append(" — ").append(m.path("role").asText())
                    .append(". Message: \"").append(m.path("message").asText()).append("\"");
            if (m.hasNonNull("linkedin")) sb.append(" (LinkedIn: ").append(m.path("linkedin").asText()).append(")");
            sb.append("\n");
        }
        sb.append("Leadership Team:\n");
        for (JsonNode l : arr(d.path("leadership"))) {
            sb.append("- ").append(l.path("name").asText()).append(" — ").append(l.path("role").asText())
                    .append(": ").append(l.path("bio").asText());
            if (l.hasNonNull("linkedin")) sb.append(" (LinkedIn: ").append(l.path("linkedin").asText()).append(")");
            sb.append("\n");
        }

        sb.append("\nOPEN CAREERS:\n");
        for (JsonNode c : arr(d.path("careers"))) {
            sb.append("- ").append(c.path("title").asText()).append(" — ").append(c.path("location").asText())
                    .append(" (").append(c.path("type").asText()).append(")\n");
        }

        sb.append("\nDOWNLOADABLE RESOURCES (the Downloads Center at /downloads offers these catalogues and nothing else):\n");
        for (JsonNode dl : arr(d.path("catalogueDownloads"))) {
            sb.append("- ").append(dl.path("title").asText()).append(" (").append(dl.path("type").asText()).append(")\n");
        }

        sb.append("\nCUSTOMER TESTIMONIALS:\n");
        for (JsonNode t : arr(d.path("testimonials"))) {
            sb.append("- \"").append(t.path("quote").asText()).append("\" — ").append(t.path("name").asText())
                    .append(", ").append(t.path("company").asText()).append("\n");
        }

        sb.append("\nFREQUENTLY ASKED QUESTIONS (answer these directly from here; do not introduce commercial figures that are not stated):\n");
        for (JsonNode g : arr(d.path("faqs"))) {
            sb.append(g.path("group").asText()).append(":\n");
            for (JsonNode it : arr(g.path("items"))) {
                sb.append("  - Q: ").append(it.path("q").asText()).append("\n    A: ").append(it.path("a").asText()).append("\n");
            }
        }

        sb.append("\nKEY PAGES ON THE WEBSITE (when it helps a visitor, link the relevant page using its exact path):\n");
        for (String[] page : KEY_PAGES) {
            sb.append("- ").append(page[1]).append(": ").append(page[0]).append("\n");
        }

        JsonNode dev = d.path("developer");
        if (dev.isObject() && !dev.isEmpty()) {
            sb.append("WEBSITE DEVELOPER (ONLY reveal this if the user explicitly asks who built / designed / "
                            + "developed / made this website — never volunteer it in any other answer):\n")
                    .append("- ").append(dev.path("name").asText()).append(" — LinkedIn: ")
                    .append(dev.path("linkedin").asText()).append("\n")
                    .append("  When asked, give the name ").append(dev.path("name").asText())
                    .append(" and link the LinkedIn profile, matching the credit in the site footer.\n");
        }

        return sb.toString();
    }

    /** One authoritative line per product, grouped by category then subcategory. */
    private String renderCatalogue(JsonNode catalogue, JsonNode categoryTree) {
        Map<String, String> catSlug = new LinkedHashMap<>();
        Map<String, String> subSlug = new LinkedHashMap<>();
        for (JsonNode c : arr(categoryTree)) {
            catSlug.put(c.path("name").asText(), c.path("slug").asText());
            for (JsonNode s : arr(c.path("subcategories"))) {
                subSlug.put(c.path("name").asText() + "|||" + s.path("name").asText(), s.path("slug").asText());
            }
        }

        Map<String, Map<String, List<JsonNode>>> byCat = new LinkedHashMap<>();
        for (JsonNode p : arr(catalogue)) {
            byCat.computeIfAbsent(p.path("category").asText(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(p.path("subcategory").asText(), k -> new ArrayList<>())
                    .add(p);
        }

        StringBuilder sb = new StringBuilder(250_000);
        boolean firstCat = true;
        for (Map.Entry<String, Map<String, List<JsonNode>>> cat : byCat.entrySet()) {
            if (!firstCat) sb.append("\n\n");
            firstCat = false;

            String cs = catSlug.getOrDefault(cat.getKey(), "");
            int total = cat.getValue().values().stream().mapToInt(List::size).sum();
            sb.append("- ").append(cat.getKey()).append(" (").append(total)
                    .append(" products — page: /products/").append(cs).append(")\n");

            boolean firstSub = true;
            for (Map.Entry<String, List<JsonNode>> sub : cat.getValue().entrySet()) {
                if (!firstSub) sb.append("\n");
                firstSub = false;

                String ss = subSlug.getOrDefault(cat.getKey() + "|||" + sub.getKey(), "");
                sb.append("    • ").append(sub.getKey()).append(" (").append(sub.getValue().size())
                        .append(") — page: /products/").append(cs).append("/").append(ss).append("\n");

                for (int i = 0; i < sub.getValue().size(); i++) {
                    if (i > 0) sb.append("\n");
                    sb.append(productLine(sub.getValue().get(i)));
                }
            }
        }
        return sb.toString();
    }

    private String productLine(JsonNode p) {
        StringBuilder line = new StringBuilder("      - ");
        String itemCode = p.path("itemCode").asText("");
        if (!itemCode.isBlank()) line.append(itemCode).append(" — ");
        line.append(p.path("name").asText()).append(" (/product/").append(p.path("id").asText()).append(")");

        String desc = p.path("description").asText("").trim();
        if (!desc.isEmpty()) line.append(" | ").append(desc);

        List<String> specs = new ArrayList<>();
        for (JsonNode s : arr(p.path("specs"))) {
            String value = s.path("value").asText("").trim();
            if (value.isEmpty()) continue;
            String label = s.path("label").asText("").replace(":", "").trim();
            // Drop a spec that only repeats the item code already printed at the start.
            if (label.equalsIgnoreCase("item no") && value.equals(itemCode)) continue;
            specs.add(label + ": " + value);
        }
        if (!specs.isEmpty()) line.append(" | ").append(String.join("; ", specs));

        return line.toString();
    }

    /** The curated item codes and size ranges from KEAA's printed catalogues. */
    private String renderItemCodes(JsonNode productCategories, JsonNode enquiryOnlySlugs) {
        List<String> enquiryOnly = new ArrayList<>();
        for (JsonNode s : arr(enquiryOnlySlugs)) enquiryOnly.add(s.asText());

        List<String> blocks = new ArrayList<>();
        for (JsonNode cat : arr(productCategories)) {
            StringBuilder sb = new StringBuilder();
            String flag = enquiryOnly.contains(cat.path("slug").asText())
                    ? " [NO CATALOGUE PAGE YET — we manufacture and quote for this; send the customer to the RFQ form, never to a product URL]"
                    : "";

            sb.append("- ").append(cat.path("name").asText()).append(flag).append(": ")
                    .append(cat.path("short").asText()).append("\n");
            sb.append("    Highlights: ")
                    .append(StreamSupport.stream(arr(cat.path("bullets")).spliterator(), false)
                            .map(JsonNode::asText).collect(Collectors.joining(", "))).append("\n");
            sb.append("    Standard: ").append(cat.path("standard").asText()).append("\n");

            List<String> families = new ArrayList<>();
            for (JsonNode f : arr(cat.path("families"))) {
                StringBuilder fb = new StringBuilder("    • " + f.path("title").asText());
                if (f.hasNonNull("spec")) fb.append("\n      Spec: ").append(f.path("spec").asText());
                List<String> items = new ArrayList<>();
                for (JsonNode it : arr(f.path("items"))) {
                    StringBuilder ib = new StringBuilder("      - " + it.path("code").asText());
                    if (it.hasNonNull("label")) ib.append(" — ").append(it.path("label").asText());
                    if (it.hasNonNull("size")) ib.append(" (").append(it.path("size").asText()).append(")");
                    items.add(ib.toString());
                }
                if (!items.isEmpty()) fb.append("\n").append(String.join("\n", items));
                families.add(fb.toString());
            }
            sb.append(String.join("\n", families));
            blocks.add(sb.toString());
        }
        return String.join("\n\n", blocks);
    }

    private String renderCompanyProfile(JsonNode c) {
        JsonNode f = c.path("facilities");
        JsonNode values = c.path("values");

        StringBuilder sb = new StringBuilder();
        sb.append("\n\nCOMPANY PROFILE:\n");
        sb.append("- Legal name: ").append(c.path("name").asText()).append(" (").append(c.path("shortName").asText())
                .append("); part of the ").append(c.path("group").asText()).append(" group. Indo-Dutch, founded ")
                .append(c.path("founded").asText()).append(". Tagline: \"").append(c.path("tagline").asText()).append("\".\n");
        sb.append("- By the numbers: ")
                .append(StreamSupport.stream(arr(c.path("stats")).spliterator(), false)
                        .map(s -> s.path("value").asText() + " " + s.path("label").asText())
                        .collect(Collectors.joining(" · "))).append(".\n");
        sb.append("- Vision: ").append(values.path("vision").asText("")).append("\n");
        sb.append("- Mission: ").append(values.path("mission").asText("")).append("\n");
        sb.append("- Core values:\n");
        for (JsonNode v : arr(values.path("values"))) sb.append("  - ").append(v.asText()).append("\n");

        sb.append("\nMANUFACTURING CAPABILITY (all in-house at the Ludhiana, India plant):\n");
        sb.append("- Facilities: ").append(f.path("area").asText()).append(" across ").append(f.path("units").asText())
                .append(" units; annual capacity ").append(f.path("capacity").asText()).append("; ")
                .append(f.path("moldRooms").asText()).append(".\n");
        sb.append("- Galvanizing: ").append(f.path("galvanizingBaths").asText()).append(", to DIN EN 1461.\n");
        sb.append("- Coating: ").append(f.path("powderCoating").asText()).append(".\n");
        sb.append("- Welding: ").append(f.path("welders").asText()).append(".\n");
        sb.append("- Certified quality: ").append(f.path("quality").asText()).append(".\n");
        sb.append("- In-house testing: ").append(f.path("testing").asText()).append(".\n");
        sb.append("- Machinery:\n");
        for (JsonNode m : arr(c.path("machinery"))) {
            sb.append("  - ").append(m.path("name").asText()).append(": ").append(m.path("desc").asText()).append("\n");
        }

        sb.append("\nMANUFACTURING PROCESS (raw material to dispatch):\n");
        for (JsonNode s : arr(c.path("processSteps"))) {
            sb.append("  ").append(s.path("step").asText()).append(". ").append(s.path("title").asText())
                    .append(": ").append(s.path("desc").asText()).append("\n");
        }

        sb.append("\nCOMPANY MILESTONES:\n");
        for (JsonNode t : arr(c.path("timeline"))) {
            sb.append("  - ").append(t.path("year").asText()).append(": ").append(t.path("title").asText())
                    .append(". ").append(t.path("desc").asText()).append("\n");
        }
        return sb.toString();
    }

    private String renderContact(JsonNode c) {
        JsonNode s = c.path("social");
        JsonNode mfg = c.path("manufacturing");
        JsonNode sales = c.path("salesOffice");

        return "\nOFFICES & CONTACT:\n"
                + "- Head Office & Manufacturing (global export desk): " + mfg.path("line1").asText() + ", "
                + mfg.path("line2").asText() + ". Phones: " + join(c.path("phones"))
                + "; landline " + join(c.path("landline")) + "; fax " + c.path("fax").asText() + ".\n"
                + "- Europe Sales Office & Warehouse: " + sales.path("line1").asText() + ", "
                + sales.path("line2").asText() + ". Phone: " + sales.path("phone").asText() + ".\n"
                + "- Emails: " + join(c.path("emails")) + ". Website: " + c.path("website").asText() + ".\n"
                + "- Which office serves you: Europe and the UK are served from the Eindhoven office; the Middle East, "
                + "Africa, Asia-Pacific, the Americas and India are served from the Ludhiana head office.\n"
                + "- Official channels: LinkedIn " + s.path("linkedin").asText() + " · YouTube "
                + s.path("youtube").asText() + " · WhatsApp " + s.path("whatsapp").asText() + ".";
    }

    // ------------------------------------------------------------------ helpers

    /** Iterating a missing or non-array node yields nothing, so callers need no null checks. */
    static Iterable<JsonNode> arr(JsonNode node) {
        return node != null && node.isArray() ? node : List.of();
    }

    private static String join(JsonNode arrayNode) {
        return StreamSupport.stream(arr(arrayNode).spliterator(), false)
                .map(JsonNode::asText)
                .collect(Collectors.joining(", "));
    }
}
