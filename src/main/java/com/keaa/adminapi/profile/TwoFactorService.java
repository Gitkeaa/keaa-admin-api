package com.keaa.adminapi.profile;

import com.keaa.adminapi.user.User;
import com.keaa.adminapi.user.UserRepository;
import dev.samstevens.totp.code.CodeVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Verifies a 2FA code — either the current TOTP or a one-time recovery code (consumed on use). */
@Service
@RequiredArgsConstructor
public class TwoFactorService {

    private final CodeVerifier codeVerifier;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    public boolean verify(User u, String code) {
        if (code == null || code.isBlank()) return false;
        String c = code.trim().replace(" ", "");
        if (u.getTwoFactorSecret() != null && codeVerifier.isValidCode(u.getTwoFactorSecret(), c)) return true;
        if (u.getTwoFactorBackupCodes() == null || u.getTwoFactorBackupCodes().isBlank()) return false;
        List<String> hashes = new ArrayList<>(Arrays.asList(u.getTwoFactorBackupCodes().split(",")));
        for (String h : hashes) {
            if (passwordEncoder.matches(c, h)) {
                hashes.remove(h);                              // recovery codes are single-use
                u.setTwoFactorBackupCodes(String.join(",", hashes));
                userRepository.save(u);
                return true;
            }
        }
        return false;
    }
}
