package com.keaa.adminapi.settings;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * /api/settings — the single site-settings row. GET returns it (creating it from the current
 * site defaults on first ever call), PUT overwrites the editable fields. Locked to SUPER_ADMIN
 * in SecurityConfig, matching the Website Settings row of the access matrix.
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SiteSettingsRepository repo;

    @GetMapping
    public SiteSettings get() {
        return repo.findById(1L).orElseGet(this::seedDefault);
    }

    @PutMapping
    public SiteSettings update(@RequestBody SiteSettings in) {
        SiteSettings s = repo.findById(1L).orElseGet(this::seedDefault);
        s.setCompanyName(in.getCompanyName());
        s.setTagline(in.getTagline());
        s.setDescription(in.getDescription());
        s.setAddressLine1(in.getAddressLine1());
        s.setAddressLine2(in.getAddressLine2());
        s.setPhones(in.getPhones());
        s.setLandlines(in.getLandlines());
        s.setEmails(in.getEmails());
        s.setFax(in.getFax());
        s.setLinkedin(in.getLinkedin());
        s.setFacebook(in.getFacebook());
        s.setInstagram(in.getInstagram());
        s.setYoutube(in.getYoutube());
        s.setWhatsapp(in.getWhatsapp());
        s.setX(in.getX());
        return repo.save(s);
    }

    /** Seeds the singleton from the values the public site currently ships with. */
    private SiteSettings seedDefault() {
        return repo.save(SiteSettings.builder()
                .id(1L)
                .companyName("KEAA International Pvt. Ltd.")
                .tagline("Built for Safety. Built to Last.")
                .description("A trusted Indo-Dutch manufacturer and exporter of scaffolding systems, "
                        + "formwork accessories, safety products, livestock housing solutions and garden hardware.")
                .addressLine1("Village Bhagwanpura, Dehlon Road")
                .addressLine2("Ludhiana – 141120, Punjab, India")
                .phones("+91 98767 01926\n+91 98729 84707")
                .landlines("+91-161-2510944\n+91-161-2510946")
                .emails("raveesh@keaa-international.net\nbhupesh@keaa-international.net\nsumit@keaa-international.net")
                .fax("+91-161-2510945")
                .linkedin("https://www.linkedin.com/company/keaa/")
                .facebook("")
                .instagram("")
                .youtube("https://www.youtube.com/@keaainternationalpvtltd5005")
                .whatsapp("https://wa.me/919872984707")
                .x("")
                .build());
    }
}
