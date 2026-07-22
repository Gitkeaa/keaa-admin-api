package com.keaa.adminapi.profile;

import com.keaa.adminapi.activity.ActivityEvent;
import com.keaa.adminapi.activity.ActivityEventRepository;
import com.keaa.adminapi.activity.ActivityService;
import com.keaa.adminapi.user.User;
import com.keaa.adminapi.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * /api/profile — the signed-in user's own account (the "My Profile" screen).
 *
 * Everything here is scoped to the caller: the current user is resolved from the JWT's
 * subject, never from a path id, so one user can never read or edit another's profile. The
 * self-editable set deliberately excludes the HR/employment fields (employee id, department,
 * designation, joining date, role, status) — those are set by an admin, not by the person.
 */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserRepository userRepository;
    private final ActivityEventRepository activityRepository;
    private final ActivityService activityService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private User current(Authentication auth) {
        return userRepository.findByEmail(auth.getName()).orElseThrow();
    }

    /** Upload a profile photo from the user's device; stored on disk and served from /uploads. */
    @PostMapping("/avatar")
    public ProfileResponse uploadAvatar(@RequestParam MultipartFile file, Authentication auth) throws IOException {
        User u = current(auth);
        Path dir = Paths.get(uploadDir, "avatars");
        Files.createDirectories(dir);
        String orig = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = orig.contains(".") ? orig.substring(orig.lastIndexOf('.')) : "";
        String stored = "u" + u.getId() + "_" + UUID.randomUUID() + ext;
        file.transferTo(dir.resolve(stored));
        u.setAvatarUrl("/uploads/avatars/" + stored);
        userRepository.save(u);
        return ProfileResponse.from(u);
    }

    @GetMapping
    public ProfileResponse me(Authentication auth) {
        return ProfileResponse.from(current(auth));
    }

    @PutMapping
    public ProfileResponse update(@RequestBody UpdateRequest req, Authentication auth, HttpServletRequest http) {
        User u = current(auth);
        if (req.name() != null) u.setName(req.name().trim());
        if (req.phone() != null) u.setPhone(req.phone().trim());
        if (req.avatarUrl() != null) u.setAvatarUrl(req.avatarUrl().trim());
        if (req.twoFactorEnabled() != null) u.setTwoFactorEnabled(req.twoFactorEnabled());
        if (req.theme() != null) u.setTheme(req.theme());
        if (req.language() != null) u.setLanguage(req.language());
        if (req.timezone() != null) u.setTimezone(req.timezone());
        if (req.dateFormat() != null) u.setDateFormat(req.dateFormat());
        if (req.notifyEmail() != null) u.setNotifyEmail(req.notifyEmail());
        if (req.notifyRfq() != null) u.setNotifyRfq(req.notifyRfq());
        if (req.notifyJobs() != null) u.setNotifyJobs(req.notifyJobs());
        if (req.notifyContact() != null) u.setNotifyContact(req.notifyContact());
        if (req.notifySecurity() != null) u.setNotifySecurity(req.notifySecurity());
        userRepository.save(u);
        activityService.record(u.getId(), "PROFILE_UPDATED", "Updated profile / preferences", http);
        return ProfileResponse.from(u);
    }

    @GetMapping("/activity")
    public List<ActivityView> activity(Authentication auth,
                                       @RequestParam(defaultValue = "20") int limit) {
        User u = current(auth);
        return activityRepository
                .findByUserIdOrderByCreatedAtDesc(u.getId(), PageRequest.of(0, Math.min(limit, 100)))
                .stream().map(ActivityView::from).toList();
    }

    /** A single JSON blob for "Download my profile" — profile plus recent activity. */
    @GetMapping("/export")
    public ResponseEntity<ProfileExport> export(Authentication auth) {
        User u = current(auth);
        List<ActivityView> events = activityRepository
                .findByUserIdOrderByCreatedAtDesc(u.getId(), PageRequest.of(0, 100))
                .stream().map(ActivityView::from).toList();
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"keaa-profile.json\"")
                .body(new ProfileExport(ProfileResponse.from(u), events));
    }

    // ---- DTOs ----
    public record ProfileResponse(
            Long id, String name, String email, String role, boolean active,
            String phone, String department, String designation, String employeeId, LocalDate joiningDate,
            String avatarUrl,
            Instant lastActive, Instant lastPasswordChangedAt, Instant createdAt, String createdBy,
            boolean twoFactorEnabled,
            String theme, String language, String timezone, String dateFormat,
            boolean notifyEmail, boolean notifyRfq, boolean notifyJobs, boolean notifyContact, boolean notifySecurity,
            String assignedCountries, String assignedCategories) {

        static ProfileResponse from(User u) {
            return new ProfileResponse(
                    u.getId(), u.getName(), u.getEmail(), u.getRole().name(), u.isActive(),
                    u.getPhone(), u.getDepartment(), u.getDesignation(), u.getEmployeeId(), u.getJoiningDate(),
                    u.getAvatarUrl(),
                    u.getLastActive(), u.getLastPasswordChangedAt(), u.getCreatedAt(), u.getCreatedBy(),
                    u.isTwoFactorEnabled(),
                    u.getTheme(), u.getLanguage(), u.getTimezone(), u.getDateFormat(),
                    u.isNotifyEmail(), u.isNotifyRfq(), u.isNotifyJobs(), u.isNotifyContact(), u.isNotifySecurity(),
                    u.getAssignedCountries(), u.getAssignedCategories());
        }
    }

    public record UpdateRequest(
            String name, String phone, String avatarUrl, Boolean twoFactorEnabled,
            String theme, String language, String timezone, String dateFormat,
            Boolean notifyEmail, Boolean notifyRfq, Boolean notifyJobs, Boolean notifyContact, Boolean notifySecurity) {}

    public record ActivityView(String type, String detail, String ip, String userAgent, Instant at) {
        static ActivityView from(ActivityEvent e) {
            return new ActivityView(e.getType(), e.getDetail(), e.getIp(), e.getUserAgent(), e.getCreatedAt());
        }
    }

    public record ProfileExport(ProfileResponse profile, List<ActivityView> activity) {}
}
