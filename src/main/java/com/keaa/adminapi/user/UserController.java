package com.keaa.adminapi.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * /api/users — team members. Responses never include the password hash (see UserDto).
 * SUPER_ADMIN only (enforced by the route being under the authenticated area; finer role
 * checks can be added with @PreAuthorize when the other dashboards arrive).
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** Only a Super Admin may touch a Super Admin account (create one, edit one, promote to one,
     *  delete or force-logout one). Senior Admin / Admin manage everyone below them. */
    private boolean isSuperAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
    }

    private static final ResponseEntity<Map<String, String>> SUPER_ONLY =
            ResponseEntity.status(403).body(Map.of("error", "Only a Super Admin can manage a Super Admin account."));

    @GetMapping
    public List<UserDto> all() {
        return userRepository.findAll().stream().map(UserDto::from).toList();
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateUserRequest req, Authentication auth) {
        if ("SUPER_ADMIN".equals(req.role()) && !isSuperAdmin(auth)) return SUPER_ONLY;
        if (userRepository.existsByEmail(req.email())) {
            return ResponseEntity.badRequest().body(Map.of("error", "A user with that email already exists."));
        }
        User u = User.builder()
                .name(req.name())
                .email(req.email())
                .password(passwordEncoder.encode(req.password()))
                .role(Role.valueOf(req.role()))
                .active(true)
                .phone(req.phone())
                .department(req.department())
                .designation(req.designation())
                .employeeId(req.employeeId())
                .joiningDate(req.joiningDate())
                .assignedCountries(req.assignedCountries())
                .assignedCategories(req.assignedCategories())
                .createdBy("Super Admin")
                .build();
        return ResponseEntity.ok(UserDto.from(userRepository.save(u)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody UpdateUserRequest req, Authentication auth) {
        return userRepository.findById(id).<ResponseEntity<?>>map(u -> {
            // Editing a Super Admin, or promoting anyone to Super Admin, is Super-Admin-only.
            if ((u.getRole() == Role.SUPER_ADMIN || "SUPER_ADMIN".equals(req.role())) && !isSuperAdmin(auth)) return SUPER_ONLY;
            if (req.name() != null) u.setName(req.name());
            if (req.role() != null) u.setRole(Role.valueOf(req.role()));
            if (req.active() != null) u.setActive(req.active());
            if (req.phone() != null) u.setPhone(req.phone());
            if (req.department() != null) u.setDepartment(req.department());
            if (req.designation() != null) u.setDesignation(req.designation());
            if (req.employeeId() != null) u.setEmployeeId(req.employeeId());
            if (req.joiningDate() != null) u.setJoiningDate(req.joiningDate());
            if (req.assignedCountries() != null) u.setAssignedCountries(req.assignedCountries());
            if (req.assignedCategories() != null) u.setAssignedCategories(req.assignedCategories());
            return ResponseEntity.ok(UserDto.from(userRepository.save(u)));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, Authentication auth) {
        User target = userRepository.findById(id).orElse(null);
        if (target == null) return ResponseEntity.noContent().build();
        if (target.getRole() == Role.SUPER_ADMIN && !isSuperAdmin(auth)) return SUPER_ONLY;
        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /** Force-log-out a user from every device — bumps their token version (Profile Visibility
     *  Matrix: "Logout All Devices — Any User"). */
    @PostMapping("/{id}/logout-all")
    public ResponseEntity<?> logoutUser(@PathVariable Long id, Authentication auth) {
        return userRepository.findById(id).<ResponseEntity<?>>map(u -> {
            if (u.getRole() == Role.SUPER_ADMIN && !isSuperAdmin(auth)) return SUPER_ONLY;
            u.setTokenVersion(u.getTokenVersion() + 1);
            userRepository.save(u);
            return ResponseEntity.ok().build();
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ---- DTOs ----
    public record UserDto(Long id, String name, String email, String role, boolean active, Instant lastActive,
                          String phone, String department, String designation, String employeeId, LocalDate joiningDate,
                          boolean twoFactorEnabled, String assignedCountries, String assignedCategories) {
        static UserDto from(User u) {
            return new UserDto(u.getId(), u.getName(), u.getEmail(), u.getRole().name(), u.isActive(), u.getLastActive(),
                    u.getPhone(), u.getDepartment(), u.getDesignation(), u.getEmployeeId(), u.getJoiningDate(),
                    u.isTwoFactorEnabled(), u.getAssignedCountries(), u.getAssignedCategories());
        }
    }

    public record CreateUserRequest(
            @NotBlank String name,
            @NotBlank @Email String email,
            @NotBlank String password,
            @NotBlank String role,
            String phone, String department, String designation, String employeeId, LocalDate joiningDate,
            String assignedCountries, String assignedCategories) {}

    public record UpdateUserRequest(String name, String role, Boolean active,
                                    String phone, String department, String designation, String employeeId, LocalDate joiningDate,
                                    String assignedCountries, String assignedCategories) {}
}
