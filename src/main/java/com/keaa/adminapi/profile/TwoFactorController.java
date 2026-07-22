package com.keaa.adminapi.profile;

import com.keaa.adminapi.activity.ActivityService;
import com.keaa.adminapi.user.User;
import com.keaa.adminapi.user.UserRepository;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.recovery.RecoveryCodeGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.util.Utils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * /api/profile/2fa — two-factor auth setup for the signed-in user, all via the TOTP library.
 * Setup issues a secret + QR; enable confirms it with a 6-digit code and hands back one-time
 * recovery codes. Scoped to the caller (email from the JWT), never a path id.
 */
@RestController
@RequestMapping("/api/profile/2fa")
@RequiredArgsConstructor
public class TwoFactorController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActivityService activityService;
    private final TwoFactorService twoFactorService;
    private final SecretGenerator secretGenerator;
    private final QrGenerator qrGenerator;
    private final CodeVerifier codeVerifier;
    private final RecoveryCodeGenerator recoveryCodeGenerator;

    private User current(Authentication auth) {
        return userRepository.findByEmail(auth.getName()).orElseThrow();
    }

    /** Start setup: mint a secret (not yet active) and return it plus a scannable QR image. */
    @GetMapping("/setup")
    public ResponseEntity<?> setup(Authentication auth) throws QrGenerationException {
        User u = current(auth);
        String secret = secretGenerator.generate();
        u.setTwoFactorSecret(secret);      // stored but 2FA stays off until confirmed
        userRepository.save(u);

        QrData data = new QrData.Builder()
                .label(u.getEmail()).secret(secret).issuer("KEAA International")
                .algorithm(HashingAlgorithm.SHA1).digits(6).period(30).build();
        String qr = Utils.getDataUriForImage(qrGenerator.generate(data), qrGenerator.getImageMimeType());
        return ResponseEntity.ok(new SetupResponse(secret, qr));
    }

    /** Confirm setup with a code from the app, switch 2FA on and return recovery codes once. */
    @PostMapping("/enable")
    public ResponseEntity<?> enable(@RequestBody CodeRequest req, Authentication auth, HttpServletRequest http) {
        User u = current(auth);
        if (u.getTwoFactorSecret() == null || !codeVerifier.isValidCode(u.getTwoFactorSecret(), req.code())) {
            return ResponseEntity.status(400).body(new Msg("That code is not valid. Try again."));
        }
        String[] codes = recoveryCodeGenerator.generateCodes(8);
        u.setTwoFactorEnabled(true);
        u.setLastTwoFactorAt(Instant.now());
        u.setTwoFactorBackupCodes(hashJoin(codes));
        userRepository.save(u);
        activityService.record(u.getId(), "TWO_FACTOR", "Enabled two-factor authentication", http);
        return ResponseEntity.ok(new CodesResponse(codes));
    }

    @PostMapping("/disable")
    public ResponseEntity<?> disable(@RequestBody CodeRequest req, Authentication auth, HttpServletRequest http) {
        User u = current(auth);
        if (!twoFactorService.verify(u, req.code())) return ResponseEntity.status(400).body(new Msg("That code is not valid."));
        u.setTwoFactorEnabled(false);
        u.setTwoFactorSecret(null);
        u.setTwoFactorBackupCodes(null);
        userRepository.save(u);
        activityService.record(u.getId(), "TWO_FACTOR", "Disabled two-factor authentication", http);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/backup-codes")
    public ResponseEntity<?> regenerate(@RequestBody CodeRequest req, Authentication auth) {
        User u = current(auth);
        if (!twoFactorService.verify(u, req.code())) return ResponseEntity.status(400).body(new Msg("That code is not valid."));
        String[] codes = recoveryCodeGenerator.generateCodes(8);
        u.setTwoFactorBackupCodes(hashJoin(codes));
        userRepository.save(u);
        return ResponseEntity.ok(new CodesResponse(codes));
    }

    // ---- helpers ----
    private String hashJoin(String[] codes) {
        List<String> hashed = new ArrayList<>();
        for (String c : codes) hashed.add(passwordEncoder.encode(c));
        return String.join(",", hashed);
    }

    public record CodeRequest(String code) {}
    public record SetupResponse(String secret, String qr) {}
    public record CodesResponse(String[] backupCodes) {}
    public record Msg(String error) {}
}
