package com.keaa.adminapi.auth;

import com.keaa.adminapi.activity.ActivityService;
import com.keaa.adminapi.profile.TwoFactorService;
import com.keaa.adminapi.security.JwtService;
import com.keaa.adminapi.user.User;
import com.keaa.adminapi.user.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;

/**
 * Login issues a JWT and sets it as an httpOnly cookie — the token never reaches JavaScript,
 * which is why the React admin uses fetch(..., { credentials: 'include' }) instead of an
 * Authorization header. Logout clears the cookie; /me returns the current profile.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActivityService activityService;
    private final TwoFactorService twoFactorService;

    @Value("${app.jwt.cookie-name}")
    private String cookieName;
    @Value("${app.jwt.expiration-days}")
    private long expirationDays;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req, HttpServletRequest request,
                                   HttpServletResponse response) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.email(), req.password()));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body(new ErrorResponse("Invalid email or password."));
        }

        User user = userRepository.findByEmail(req.email()).orElseThrow();

        // If 2FA is on, the password is only step one: hand back a short-lived challenge token
        // and STOP — no session cookie until /login/2fa verifies the code.
        if (user.isTwoFactorEnabled()) {
            return ResponseEntity.ok(new TwoFactorChallenge(true, jwtService.generatePreAuthToken(user.getEmail())));
        }

        issueSession(user, request, response, "Signed in");
        return ResponseEntity.ok(UserResponse.from(user));
    }

    /** Step two of a 2FA login: trade the challenge token + a valid code for a real session. */
    @PostMapping("/login/2fa")
    public ResponseEntity<?> loginTwoFactor(@RequestBody TwoFactorLoginRequest req, HttpServletRequest request,
                                            HttpServletResponse response) {
        String email;
        try {
            Claims c = jwtService.parse(req.challengeToken());
            if (!"2fa".equals(c.get("typ", String.class))) throw new IllegalStateException();
            email = c.getSubject();
        } catch (Exception e) {
            return ResponseEntity.status(401).body(new ErrorResponse("Your sign-in expired. Please log in again."));
        }
        User user = userRepository.findByEmail(email).filter(User::isActive).orElse(null);
        if (user == null || !user.isTwoFactorEnabled()) return ResponseEntity.status(401).build();
        if (!twoFactorService.verify(user, req.code())) {
            return ResponseEntity.status(400).body(new ErrorResponse("That code is not valid."));
        }
        user.setLastTwoFactorAt(Instant.now());
        issueSession(user, request, response, "Signed in (2FA)");
        return ResponseEntity.ok(UserResponse.from(user));
    }

    private void issueSession(User user, HttpServletRequest request, HttpServletResponse response, String detail) {
        user.setLastActive(Instant.now());
        userRepository.save(user);
        activityService.record(user.getId(), "LOGIN", detail, request);
        String token = jwtService.generateToken(user);
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(token, Duration.ofDays(expirationDays)).toString());
    }

    /**
     * "Log out of all devices" — bumps the user's token version so every JWT minted before now
     * fails the JwtAuthFilter check, then re-issues a fresh cookie so the CURRENT session (the
     * one that asked) stays signed in.
     */
    @PostMapping("/logout-all")
    public ResponseEntity<?> logoutAll(Authentication authentication, HttpServletRequest request,
                                       HttpServletResponse response) {
        if (authentication == null) return ResponseEntity.status(401).build();
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        activityService.record(user.getId(), "LOGOUT_ALL", "Logged out of all other devices", request);

        String token = jwtService.generateToken(user);
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(token, Duration.ofDays(expirationDays)).toString());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(Authentication authentication, HttpServletRequest request,
                                    HttpServletResponse response) {
        // Record the sign-out on the user's activity timeline before clearing the cookie. The
        // session is still valid at this point (JwtAuthFilter ran first), so Authentication is
        // present; best-effort, so a missing user never blocks the logout itself.
        if (authentication != null) {
            userRepository.findByEmail(authentication.getName())
                    .ifPresent(u -> activityService.record(u.getId(), "LOGOUT", "Signed out", request));
        }
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie("", Duration.ZERO).toString());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null) return ResponseEntity.status(401).build();
        return userRepository.findByEmail(authentication.getName())
                .<ResponseEntity<?>>map(u -> ResponseEntity.ok(UserResponse.from(u)))
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    /**
     * Lets a signed-in user change their own password from the Profile screen. Under
     * /api/auth/** (permitAll in SecurityConfig), so the null-authentication guard is what
     * keeps it private — an unauthenticated caller has no Authentication and gets 401.
     */
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest req,
                                            Authentication authentication, HttpServletRequest request) {
        if (authentication == null) return ResponseEntity.status(401).build();
        User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
        if (!passwordEncoder.matches(req.currentPassword(), user.getPassword())) {
            return ResponseEntity.status(400).body(new ErrorResponse("Current password is incorrect."));
        }
        user.setPassword(passwordEncoder.encode(req.newPassword()));
        user.setLastPasswordChangedAt(Instant.now());
        userRepository.save(user);
        activityService.record(user.getId(), "PASSWORD_CHANGED", "Changed password", request);
        return ResponseEntity.ok().build();
    }

    private ResponseCookie buildCookie(String value, Duration maxAge) {
        return ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(false)   // dev over http; set true behind https in production
                .path("/")
                .sameSite("Lax")
                .maxAge(maxAge)
                .build();
    }

    // ---- DTOs ----
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 6, message = "New password must be at least 6 characters.") String newPassword) {}

    /** Returned by /login when 2FA is on — no session yet, just the challenge to complete. */
    public record TwoFactorChallenge(boolean twoFactorRequired, String challengeToken) {}

    public record TwoFactorLoginRequest(String challengeToken, String code) {}

    public record UserResponse(Long id, String name, String email, String role, String avatarUrl, String phone) {
        static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRole().name(), u.getAvatarUrl(), u.getPhone());
        }
    }

    public record ErrorResponse(String error) {}
}
