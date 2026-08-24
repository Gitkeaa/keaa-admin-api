package com.keaa.adminapi.chat;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static com.keaa.adminapi.chat.KnowledgeBaseService.arr;

/** The answer a visitor gets when the Claude API cannot be reached.
 *
 *  Everything the assistant knows is already in memory, so instead of showing an outage
 *  message this searches that content directly: a keyword match over the catalogue, FAQ,
 *  contact details, certifications, downloads and careers. It is not a conversation, but it
 *  is real information rather than a dead end. */
@Service
@RequiredArgsConstructor
public class OfflineAnswerService {

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "you", "your", "are", "can", "with", "what", "who", "how", "why",
            "does", "did", "has", "have", "this", "that", "they", "them", "from", "about", "any",
            "all", "get", "got", "let", "know", "tell", "give", "need", "want", "please", "their",
            "there", "been", "was", "were", "will", "would", "could", "should", "keaa", "international");

    private static final String OFFLINE_NOTE =
            "_The AI assistant is offline right now, so this is a direct search of our site data._";

    private static final String RFQ_LINE =
            "For prices, minimum order quantity, lead times, samples, OEM or custom work, please use the "
                    + "[Request a Quote](/contact?tab=rfq) form. We do not publish figures, our team quotes each enquiry.";

    // Each pattern anchors on a word start but deliberately has no closing boundary, so a stem
    // matches its whole family: "certif" catches certificate and certifications.
    private static final Pattern CONTACT = Pattern.compile(
            "\\b(contact|phone|call|email|e-mail|mail|address|office|located|location|whatsapp|reach us|get in touch)");
    private static final Pattern PRICING = Pattern.compile(
            "\\b(price|pricing|cost|quote|quotation|moq|minimum order|lead time|delivery time|sample|oem|custom|discount|payment term)");
    private static final Pattern CERTS = Pattern.compile(
            "(\\bcertif|\\biso\\b|9001|14001|45001|\\ben ?1090\\b|3834|1461|\\bgalvani|\\bcomplian|\\baccredit|test report|[üu].?mark)");
    private static final Pattern DOWNLOADS = Pattern.compile(
            "\\b(catalogue|catalog|brochure|pdf|download|datasheet|data sheet|spec sheet)");
    private static final Pattern DEVELOPER = Pattern.compile(
            "\\bwho\\s+(built|made|designed|developed|created)\\b|\\b(website|web|site)\\s+(developer|designer)");
    private static final Pattern LEADERSHIP = Pattern.compile(
            "\\b(chairman|managing director|founder|leadership|management team|board|\\bceo\\b|who runs|who leads|who owns)");
    private static final Pattern CAREERS = Pattern.compile(
            "\\b(career|job|vacanc|hiring|recruit|internship|employment)");

    private final KnowledgeBaseService knowledgeBase;

    /** Best-effort answer built from the site's own data. Returns null only when the knowledge
     *  base failed to load, in which case the caller sends a plain apology. */
    public String answer(String question) {
        if (!knowledgeBase.isLoaded()) return null;

        JsonNode d = knowledgeBase.getData();
        JsonNode company = d.path("company");
        String q = question == null ? "" : question.toLowerCase();
        List<String> tokens = tokenize(q);

        if (CONTACT.matcher(q).find()) return say(contactBlock(company));

        if (PRICING.matcher(q).find()) {
            return say(RFQ_LINE + "\n\nYou can also reach the team directly, see [Contact Us](/contact).");
        }

        if (CERTS.matcher(q).find()) {
            StringBuilder sb = new StringBuilder("Our certifications:\n\n");
            for (JsonNode c : arr(company.path("certifications"))) {
                sb.append("- **").append(c.path("name").asText()).append("** (")
                        .append(c.path("body").asText()).append("): ").append(c.path("note").asText()).append("\n");
            }
            sb.append("\nFull details, including test reports, are on the [Certifications](/certifications) page.");
            return say(sb.toString());
        }

        if (DOWNLOADS.matcher(q).find()) {
            StringBuilder sb = new StringBuilder("These catalogues are available:\n\n");
            for (JsonNode dl : arr(d.path("catalogueDownloads"))) {
                sb.append("- ").append(dl.path("title").asText())
                        .append(" (").append(dl.path("type").asText()).append(")\n");
            }
            sb.append("\nDownload them from the [Downloads Center](/downloads).");
            return say(sb.toString());
        }

        // Checked before leadership so "who developed this website" is not answered with a list
        // of directors. The developer credit is given only when explicitly asked for.
        if (DEVELOPER.matcher(q).find()) {
            JsonNode dev = d.path("developer");
            return say(dev.isObject() && !dev.isEmpty()
                    ? "This website was built by " + dev.path("name").asText()
                            + ". LinkedIn: " + dev.path("linkedin").asText()
                    : "I do not have that on file. Please ask us through the [Contact Us](/contact) page.");
        }

        if (LEADERSHIP.matcher(q).find()) {
            List<String> people = new ArrayList<>();
            JsonNode chairman = d.path("chairman");
            if (chairman.isObject() && !chairman.isEmpty()) {
                people.add("- **" + chairman.path("name").asText() + "**, " + chairman.path("role").asText());
            }
            for (JsonNode m : arr(d.path("managingDirectors"))) {
                people.add("- **" + m.path("name").asText() + "**, " + m.path("role").asText());
            }
            for (JsonNode l : arr(d.path("leadership"))) {
                people.add("- **" + l.path("name").asText() + "**, " + l.path("role").asText());
            }
            return say("Our leadership:\n\n" + String.join("\n", people)
                    + "\n\nTheir full profiles and messages are on the [About Us](/about) page.");
        }

        if (CAREERS.matcher(q).find()) {
            List<String> roles = new ArrayList<>();
            for (JsonNode c : arr(d.path("careers"))) {
                roles.add("- " + c.path("title").asText() + ", " + c.path("location").asText()
                        + " (" + c.path("type").asText() + ")");
            }
            String list = roles.isEmpty() ? "There are no roles listed at the moment." : String.join("\n", roles);
            return say("Current openings:\n\n" + list + "\n\nApply through the [Careers](/careers) page.");
        }

        String products = productMatches(d, tokens);
        if (products != null) return say(products);

        String faq = faqMatches(d, tokens);
        if (faq != null) return say(faq);

        return say(String.join("\n",
                "I could not match that to anything specific while offline. These pages cover most questions:",
                "",
                "- [All Products](/products): scaffolding, formwork accessories, livestock housing and garden hardware",
                "- [Manufacturing](/manufacturing): our facilities, machinery and process",
                "- [Certifications](/certifications): ISO, EN 1090 and welding approvals",
                "- [FAQ](/faq): ordering, finishes, lead times and export",
                "- [Request a Quote](/contact?tab=rfq): prices, minimum order and custom work",
                "- [Contact Us](/contact): offices, phone and email"));
    }

    // ------------------------------------------------------------------ sections

    private String contactBlock(JsonNode company) {
        JsonNode mfg = company.path("manufacturing");
        JsonNode sales = company.path("salesOffice");
        return String.join("\n",
                "**Head office and manufacturing (global export desk)**",
                mfg.path("line1").asText() + ", " + mfg.path("line2").asText(),
                "Phone: " + joinText(company.path("phones")),
                "",
                "**Europe sales office and warehouse**",
                sales.path("line1").asText() + ", " + sales.path("line2").asText(),
                "Phone: " + sales.path("phone").asText(),
                "",
                "Email: " + joinText(company.path("emails")),
                "",
                "Full details are on the [Contact Us](/contact) page.");
    }

    private String productMatches(JsonNode d, List<String> tokens) {
        Map<String, String> catSlug = new LinkedHashMap<>();
        for (JsonNode c : arr(d.path("categoryTree"))) {
            catSlug.put(c.path("name").asText(), c.path("slug").asText());
        }

        List<Scored> scored = new ArrayList<>();
        for (JsonNode p : arr(d.path("catalogue"))) {
            // A name or item-code hit is a far stronger signal than a stray word in a spec.
            int score = score(p.path("name").asText(), tokens) * 3
                    + score(p.path("itemCode").asText(), tokens) * 3
                    + score(p.path("subcategory").asText(), tokens) * 2
                    + score(p.path("category").asText(), tokens)
                    + score(p.path("description").asText(), tokens);
            if (score > 0) scored.add(new Scored(p, score));
        }
        if (scored.isEmpty()) return null;

        scored.sort(Comparator.comparingInt(Scored::score).reversed());
        List<Scored> top = scored.subList(0, Math.min(6, scored.size()));

        StringBuilder sb = new StringBuilder("Here is what matches in our catalogue:\n\n");
        Set<String> categories = new LinkedHashSet<>();
        for (Scored s : top) {
            JsonNode p = s.node();
            String code = p.path("itemCode").asText("");
            sb.append("- [").append(p.path("name").asText()).append("](/product/").append(p.path("id").asText())
                    .append(") (").append(code.isBlank() ? "" : code + ", ").append(p.path("category").asText()).append(")\n");
            categories.add(p.path("category").asText());
        }

        List<String> catLinks = new ArrayList<>();
        for (String name : categories) {
            catLinks.add("[" + name + "](/products/" + catSlug.getOrDefault(name, "") + ")");
        }

        sb.append("\nBrowse the full range under ").append(String.join(", ", catLinks))
                .append(", or see [all products](/products).\n\n").append(RFQ_LINE);
        return sb.toString();
    }

    private String faqMatches(JsonNode d, List<String> tokens) {
        List<Scored> scored = new ArrayList<>();
        for (JsonNode group : arr(d.path("faqs"))) {
            for (JsonNode item : arr(group.path("items"))) {
                int s = score(item.path("q").asText(), tokens) * 2 + score(item.path("a").asText(), tokens);
                if (s > 1) scored.add(new Scored(item, s));
            }
        }
        if (scored.isEmpty()) return null;

        scored.sort(Comparator.comparingInt(Scored::score).reversed());
        List<String> parts = new ArrayList<>();
        for (Scored s : scored.subList(0, Math.min(2, scored.size()))) {
            parts.add("**" + s.node().path("q").asText() + "**\n\n" + s.node().path("a").asText());
        }
        parts.add("More questions are answered on the [FAQ](/faq) page.");
        return String.join("\n\n", parts);
    }

    // ------------------------------------------------------------------ helpers

    private record Scored(JsonNode node, int score) {}

    private static String say(String body) {
        return OFFLINE_NOTE + "\n\n" + body;
    }

    private static List<String> tokenize(String question) {
        Set<String> out = new LinkedHashSet<>();
        for (String word : question.toLowerCase().split("[^a-z0-9]+")) {
            if (word.length() > 2 && !STOP_WORDS.contains(word)) out.add(word);
        }
        return new ArrayList<>(out);
    }

    /** How many of the query's words appear in a block of text. */
    private static int score(String text, List<String> tokens) {
        if (text == null || text.isEmpty()) return 0;
        String haystack = text.toLowerCase();
        int n = 0;
        for (String token : tokens) if (haystack.contains(token)) n++;
        return n;
    }

    private static String joinText(JsonNode arrayNode) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : arr(arrayNode)) out.add(n.asText());
        return String.join(", ", out);
    }
}
